#!/usr/bin/env python3
"""Compile test of a folder of circuits (e.g. a course's) with Logisim's openXC7 flow, or another board's:

  support/openxc7/tests/run_course_test.py <folder> [--board NAME] [--jobs N] [--only TEXT] [--exclude TEXT]

For each .circ in the folder (except paths containing --exclude, default "Logisim_toys") and its main circuit:
complete its board map automatically (lstest.AutoMap: TTY/Keyboard to the UART, 1-bit inputs/outputs to the
buttons/LEDs, the rest to the header pins), then run Logisim's whole FPGA flow (--test-fpga: DRC, HDL, Yosys,
nextpnr-xilinx, Project X-Ray) with a stand-in for openFPGALoader that only checks the bitstream, so the board
is not touched. Prints a table; details in build/course/<circuit>/ and build/course/results.json (for another
board than the Cmod A7, build/course_<board>/).

Needs the built jar (./gradlew shadowJar), a JDK and the board's toolchain (~/openxc7 or $OPENXC7_DIR for the
Cmod A7, ~/gowin or $GOWIN_DIR for --board SIPEED_TANG_NANO_20K or SIPEED_TANG_NANO_9K).
"""
import argparse, concurrent.futures, contextlib, json, os, pathlib, re, signal, subprocess, time
import xml.etree.ElementTree as ET

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[2]
OUT = HERE / "build"
# For each board: the folder of its toolchain (and the variable that moves it), the preference that holds Logisim's
# tool path for that flow, what of the toolchain the test's tool folder links to, and the programs it runs.
BOARDS = {
    "DIGILENT_CMOD_A7_35T": dict(tools="openxc7", variable="OPENXC7_DIR", preference="OpenXc7ToolPath",
                                 folders=["chipdb", "prjxray-db", "prjxray", "pyenv", "nextpnr-xilinx", "yosys"],
                                 programs=["yosys", "nextpnr-xilinx", "fasm2frames", "xc7frames2bit"]),
    "SIPEED_TANG_NANO_20K": dict(tools="gowin", variable="GOWIN_DIR", preference="GowinToolPath",
                                 folders=["pyenv", "share"], programs=["yosys", "nextpnr-himbaechel", "gowin_pack"]),
    "SIPEED_TANG_NANO_9K": dict(tools="gowin", variable="GOWIN_DIR", preference="GowinToolPath",
                                folders=["pyenv", "share"], programs=["yosys", "nextpnr-himbaechel", "gowin_pack"]),
}

parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
parser.add_argument("folder", type=pathlib.Path)
parser.add_argument("--jobs", type=int, default=3)
parser.add_argument("--only", default="", help="test only paths containing this text")
parser.add_argument("--exclude", default="Logisim_toys", help="skip paths containing this text")
parser.add_argument("--board", choices=BOARDS, default="DIGILENT_CMOD_A7_35T")
args = parser.parse_args()
BOARD = args.board
SETUP = BOARDS[BOARD]
TOOLCHAIN = pathlib.Path(os.environ.get(SETUP["variable"], pathlib.Path.home() / SETUP["tools"]))
COURSE = OUT / ("course" if BOARD == "DIGILENT_CMOD_A7_35T" else f"course_{BOARD}")

jars = sorted((REPO / "build" / "libs").glob("logisim-evolution-*-all.jar"))
if not jars:
    raise SystemExit("build Logisim first: ./gradlew shadowJar")
# the newest, as a checkout can still hold the jar of an earlier version under an earlier name
JAR = max(jars, key=lambda jar: jar.stat().st_mtime)
(OUT / "classes").mkdir(parents=True, exist_ok=True)
subprocess.run(["javac", "-nowarn", "-d", str(OUT / "classes"), "-cp", str(JAR),
                *map(str, HERE.glob("harness/*/*.java"))], check=True)

# a tool folder like the board's (~/openxc7, ~/gowin) whose openFPGALoader only checks that the bitstream is there
tools = OUT / ("testroot" if BOARD == "DIGILENT_CMOD_A7_35T" else f"testroot_{SETUP['tools']}")
(tools / "bin").mkdir(parents=True, exist_ok=True)
for name in SETUP["folders"]:
    link = tools / name
    if (TOOLCHAIN / name).exists() and not link.is_symlink():
        link.symlink_to(TOOLCHAIN / name)
for name in SETUP["programs"]:
    link = tools / "bin" / name
    if not link.is_symlink():
        link.symlink_to(TOOLCHAIN / "bin" / name)
stub = tools / "bin" / "openFPGALoader"
stub.write_text('#!/bin/sh\n# test stand-in: check the bitstream instead of loading it\nfor a; do last="$a"; done\n'
                '[ -s "$last" ] || { echo "STUB openFPGALoader: no bitstream $last"; exit 1; }\n'
                'echo "STUB openFPGALoader: $*"\n')
stub.chmod(0o755)


def kill_with_descendants(pid):
    """Kills a process and all that it started. They are found by their parents rather than put in a process group
    of their own, as they have to stay in the test's: Ctrl-C and a closed terminal end them through it."""
    with contextlib.suppress(ProcessLookupError):       # here and below: it may have ended by itself
        os.kill(pid, signal.SIGSTOP)                    # so that it starts nothing more meanwhile
    children = {}
    table = subprocess.run(["ps", "-A", "-o", "pid=", "-o", "ppid="], capture_output=True, text=True, check=True)
    for line in table.stdout.splitlines():
        child, parent = map(int, line.split())
        children.setdefault(parent, []).append(child)
    doomed = [pid]
    for each in doomed:                                 # the list grows by each one's children
        doomed.extend(children.get(each, []))
    for each in doomed:
        with contextlib.suppress(ProcessLookupError):
            os.kill(each, signal.SIGKILL)


def java(prefs, *arguments):
    command = ["java", "-Djava.util.prefs.PreferencesFactory=isoprefs.FilePrefsFactory", f"-Disoprefs.file={prefs}",
               "-Djava.awt.headless=true", "-cp", f"{OUT / 'classes'}{os.pathsep}{JAR}", *arguments]
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        out, err = process.communicate(timeout=1800)
        return process.returncode, out + err
    except subprocess.TimeoutExpired:
        # killing Logisim alone left a nextpnr behind that went on searching; the circuit fails as "timeout"
        if os.name != "nt":
            kill_with_descendants(process.pid)
        else:
            process.kill()
        out, err = process.communicate()
        return "timeout", out + err + "\nERROR: timeout after 1800 s\n"


def lines_of(log):
    return [re.sub(r"^\[main\] [A-Z]+ com\.cburch\.logisim\.fpga\.gui\.Reporter - ", "", line) for line in log.splitlines()]


def run(circ):
    rel = circ.relative_to(args.folder)
    name = re.sub(r"[^A-Za-z0-9_]", "_", str(rel.with_suffix("")))
    work = COURSE / name
    work.mkdir(parents=True, exist_ok=True)
    prefs = work / "prefs.properties"
    prefs.write_text(f"/com/cburch/logisim|FPGAWorkspace={work}/ws\n/com/cburch/logisim|hdlType=Verilog\n"
                     f"/com/cburch/logisim|{SETUP['preference']}={tools / 'bin'}/\n")
    top = ET.parse(circ).getroot().find("main").get("name")
    result = {"file": str(rel), "top": top}
    start = time.time()
    code, log = java(prefs, "lstest.AutoMap", str(circ), top, BOARD, str(work / f"{name}.circ"))
    (work / "automap.log").write_text(log)
    lines = lines_of(log)
    result["automap"] = [line[9:] for line in lines if line.startswith("AUTOMAP: ")]
    if code != 0:
        result["result"] = "DRC failed" if code == 2 else f"automap error {code}"
        result["errors"] = [line for line in lines if "ERROR" in line or "Exception" in line or line.startswith("Found")][:10]
        result["seconds"] = round(time.time() - start, 1)
        return result
    code, log = java(prefs, "com.cburch.logisim.Main", "--test-fpga", str(work / f"{name}.circ"), top, BOARD)
    (work / "flow.log").write_text(log)
    lines = lines_of(log)
    result["seconds"] = round(time.time() - start, 1)
    fmax = [line for line in lines if "Max frequency for clock" in line]
    result["fmax"] = fmax[-1].split(": ", 1)[1] if fmax else ""
    result["errors"] = [line for line in lines if re.search(r"ERROR|Error found|error:", line)][:10]
    loaded = code == 0 and any("STUB openFPGALoader" in line for line in lines)
    result["result"] = "bitstream" if loaded else f"failed ({code})"
    return result


circuits = sorted(p for p in args.folder.rglob("*.circ") if args.exclude not in str(p) and args.only in str(p))
print(f"{len(circuits)} circuits, {args.jobs} at a time")
results = []
with concurrent.futures.ThreadPoolExecutor(args.jobs) as pool:
    for result in pool.map(run, circuits):
        results.append(result)
        print(f"{result['result']:>12}  {result['seconds']:6}s  {result['file']} [{result['top']}]  {result.get('fmax', '')}")
        for error in result.get("errors", [])[:2]:
            print(f"{'':14}{error[:160]}")
COURSE.mkdir(parents=True, exist_ok=True)
(COURSE / "results.json").write_text(json.dumps(results, indent=1))
built = sum(r["result"] == "bitstream" for r in results)
print(f"\n{built} of {len(results)} built to a bitstream")
