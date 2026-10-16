#!/usr/bin/env python3
"""Compile test of a folder of circuits (e.g. a course's) with Logisim's openXC7 flow:

  support/openxc7/tests/run_course_test.py <folder> [--jobs N] [--only TEXT] [--exclude TEXT]

For each .circ in the folder (except paths containing --exclude, default "Logisim_toys") and its main circuit:
complete its board map automatically (lstest.AutoMap: TTY/Keyboard to the UART, 1-bit inputs/outputs to the
buttons/LEDs, the rest to the header pins), then run Logisim's whole FPGA flow (--test-fpga: DRC, HDL, Yosys,
nextpnr-xilinx, Project X-Ray) with a stand-in for openFPGALoader that only checks the bitstream, so the board
is not touched. Prints a table; details in build/course/<circuit>/ and build/course/results.json.

Needs the built jar (./gradlew shadowJar), a JDK and the toolchain (~/openxc7, or $OPENXC7_DIR).
"""
import argparse, concurrent.futures, json, os, pathlib, re, subprocess, time, xml.etree.ElementTree as ET

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[2]
OUT = HERE / "build"
BOARD = "DIGILENT_CMOD_A7_35T"
TOOLCHAIN = pathlib.Path(os.environ.get("OPENXC7_DIR", pathlib.Path.home() / "openxc7"))

parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
parser.add_argument("folder", type=pathlib.Path)
parser.add_argument("--jobs", type=int, default=3)
parser.add_argument("--only", default="", help="test only paths containing this text")
parser.add_argument("--exclude", default="Logisim_toys", help="skip paths containing this text")
args = parser.parse_args()

jars = sorted((REPO / "build" / "libs").glob("logisim-evolution-*-all.jar"))
if not jars:
    raise SystemExit("build Logisim first: ./gradlew shadowJar")
JAR = jars[0]
(OUT / "classes").mkdir(parents=True, exist_ok=True)
subprocess.run(["javac", "-nowarn", "-d", str(OUT / "classes"), "-cp", str(JAR),
                *map(str, HERE.glob("harness/*/*.java"))], check=True)

# a tool folder like ~/openxc7 whose openFPGALoader only checks that the bitstream is there
tools = OUT / "testroot"
(tools / "bin").mkdir(parents=True, exist_ok=True)
for name in ["chipdb", "prjxray-db", "prjxray", "pyenv", "nextpnr-xilinx", "yosys"]:
    link = tools / name
    if (TOOLCHAIN / name).exists() and not link.is_symlink():
        link.symlink_to(TOOLCHAIN / name)
for name in ["yosys", "nextpnr-xilinx", "fasm2frames", "xc7frames2bit"]:
    link = tools / "bin" / name
    if not link.is_symlink():
        link.symlink_to(TOOLCHAIN / "bin" / name)
stub = tools / "bin" / "openFPGALoader"
stub.write_text('#!/bin/sh\n# test stand-in: check the bitstream instead of loading it\nfor a; do last="$a"; done\n'
                '[ -s "$last" ] || { echo "STUB openFPGALoader: no bitstream $last"; exit 1; }\n'
                'echo "STUB openFPGALoader: $*"\n')
stub.chmod(0o755)


def java(prefs, *arguments):
    command = ["java", "-Djava.util.prefs.PreferencesFactory=isoprefs.FilePrefsFactory", f"-Disoprefs.file={prefs}",
               "-Djava.awt.headless=true", "-cp", f"{OUT / 'classes'}{os.pathsep}{JAR}", *arguments]
    result = subprocess.run(command, capture_output=True, text=True, timeout=1800)
    return result.returncode, result.stdout + result.stderr


def lines_of(log):
    return [re.sub(r"^\[main\] [A-Z]+ com\.cburch\.logisim\.fpga\.gui\.Reporter - ", "", line) for line in log.splitlines()]


def run(circ):
    rel = circ.relative_to(args.folder)
    name = re.sub(r"[^A-Za-z0-9_]", "_", str(rel.with_suffix("")))
    work = OUT / "course" / name
    work.mkdir(parents=True, exist_ok=True)
    prefs = work / "prefs.properties"
    prefs.write_text(f"/com/cburch/logisim|FPGAWorkspace={work}/ws\n/com/cburch/logisim|hdlType=Verilog\n"
                     f"/com/cburch/logisim|OpenXc7ToolPath={tools / 'bin'}/\n")
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
(OUT / "course").mkdir(parents=True, exist_ok=True)
(OUT / "course" / "results.json").write_text(json.dumps(results, indent=1))
built = sum(r["result"] == "bitstream" for r in results)
print(f"\n{built} of {len(results)} built to a bitstream")
