#!/usr/bin/env python3
"""On-board test of a circuit with a Keyboard and a TTY, through Logisim's whole FPGA flow, on a Cmod A7-35T:

  support/openxc7/tests/board/boardtest.py [--reference other.circ] [--seconds S] <file.circ> <top> <tick Hz> \
        [--] <input> [<input> ...]              (-- before inputs that start with '-')

The circuit's board map is completed automatically (TTY/Keyboard to the UART), then Logisim builds and loads it
(--test-fpga). For each input the bitstream is loaded again (a fresh start, like a new simulation), the input
is typed on the board's USB-UART ('\\n' = Enter, sent as CR like a terminal does), and what comes back must be what
Logisim's own simulation (--tty tty) prints for the same input, shown as on a terminal. --reference simulates
another file (e.g. one without a POR, which the --tty mode does not release); --seconds is the simulation time.

Needs the built jar, a JDK, the toolchain (~/openxc7, or $OPENXC7_DIR) and the board.
"""
import argparse, glob, os, pathlib, re, select, subprocess, sys, termios, time

HERE = pathlib.Path(__file__).resolve().parent
TESTS = HERE.parent
REPO = TESTS.parents[2]
OUT = TESTS / "build" / "board"
TOOLCHAIN = pathlib.Path(os.environ.get("OPENXC7_DIR", pathlib.Path.home() / "openxc7"))
LOADER = TOOLCHAIN / "bin" / "openFPGALoader"
BOARD = "DIGILENT_CMOD_A7_35T"

parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
parser.add_argument("circ", type=pathlib.Path)
parser.add_argument("top")
parser.add_argument("frequency")
parser.add_argument("inputs", nargs="+")
parser.add_argument("--reference", type=pathlib.Path)
parser.add_argument("--seconds", type=float, default=5)
args = parser.parse_args()

JAR = sorted((REPO / "build" / "libs").glob("logisim-evolution-*-all.jar"))[0]
CLASSES = TESTS / "build" / "classes"
CLASSES.mkdir(parents=True, exist_ok=True)
subprocess.run(["javac", "-nowarn", "-d", str(CLASSES), "-cp", str(JAR), *map(str, TESTS.glob("harness/*/*.java"))],
               check=True)
name = re.sub(r"[^A-Za-z0-9_]", "_", args.circ.stem)
work = OUT / name
work.mkdir(parents=True, exist_ok=True)
prefs = work / "prefs.properties"
prefs.write_text(f"/com/cburch/logisim|FPGAWorkspace={work}/ws\n/com/cburch/logisim|hdlType=Verilog\n"
                 f"/com/cburch/logisim|OpenXc7ToolPath={LOADER.parent}/\n")


def java(*arguments):
    return ["java", "-Djava.util.prefs.PreferencesFactory=isoprefs.FilePrefsFactory", f"-Disoprefs.file={prefs}",
            "-Djava.awt.headless=true", "-cp", f"{CLASSES}{os.pathsep}{JAR}", *arguments]


def as_terminal(raw):
    """What the TTY shows, as the FPGA's TTY sends it (see TtyHdlGeneratorFactory), with CR LF read as LF."""
    out = []
    for c in raw:
        if c in "\n\r": out.append("\n")
        elif c == "\b": out.append("\b \b")
        elif c == "\x0c": out.append("\x1b[2J\x1b[H")
        elif " " <= c < "\x7f": out.append(c)
    return "".join(out)


def reference(text):
    """Logisim's simulation of the reference circuit: stdin -> Keyboard, TTY -> stdout."""
    sim = subprocess.Popen(java("com.cburch.logisim.Main", "--tty", "tty", str(args.reference or args.circ)),
                           stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    time.sleep(2)
    sim.stdin.write(text.encode())
    sim.stdin.flush()
    time.sleep(args.seconds)
    sim.kill()
    return as_terminal(sim.stdout.read().decode("ascii", "replace"))


def serial_port():
    if sys.platform == "darwin":
        ports = sorted(glob.glob("/dev/cu.usbserial-*1"))
    else:
        ports = sorted(glob.glob("/dev/serial/by-id/*Digilent*-if01-port0")) or ["/dev/ttyUSB1"]
    return ports[0]


mapped = work / f"{name}_mapped.circ"
automap = subprocess.run(java("lstest.AutoMap", str(args.circ), args.top, BOARD, str(mapped)), capture_output=True,
                         text=True)
if automap.returncode != 0:
    sys.exit(f"automatic board map failed:\n{automap.stdout[-2000:]}")
start = time.time()
flow = subprocess.run(java("com.cburch.logisim.Main", "--test-fpga", str(mapped), args.top, BOARD, args.frequency),
                      capture_output=True, text=True)
(work / "flow.log").write_text(flow.stdout + flow.stderr)
print(f"{name}: Logisim flow exit {flow.returncode} in {time.time() - start:.0f} s (log: {work / 'flow.log'})")
if flow.returncode != 0:
    sys.exit(1)
bitstream = next(work.glob("ws/**/toplevel.bit"))

failures = 0
for index, raw in enumerate(args.inputs):
    text = raw.encode().decode("unicode_escape")
    if index > 0:
        subprocess.run([str(LOADER), "-b", "cmoda7_35t", str(bitstream)], capture_output=True)
    time.sleep(3)                                      # e.g. for a power-on reset of 2 s
    fd = os.open(serial_port(), os.O_RDWR | os.O_NOCTTY | os.O_NONBLOCK)
    cc = termios.tcgetattr(fd)[6]
    cc[termios.VMIN], cc[termios.VTIME] = 0, 0
    termios.tcsetattr(fd, termios.TCSANOW, [0, 0, termios.CS8 | termios.CREAD | termios.CLOCAL, 0,
                                            termios.B115200, termios.B115200, cc])
    termios.tcflush(fd, termios.TCIOFLUSH)
    for byte in text.replace("\n", "\r").encode():
        os.write(fd, bytes([byte]))
        time.sleep(0.02)
    reply, end = b"", time.time() + 2
    while time.time() < end:
        if select.select([fd], [], [], 0.05)[0]:
            reply += os.read(fd, 4096)
    os.close(fd)
    got = reply.decode("ascii", "replace").replace("\r\n", "\n")
    want = reference(text)
    ok = got == want
    failures += not ok
    print(f"  {'ok  ' if ok else 'FAIL'} {text!r:26} -> {got!r}" + ("" if ok else f"   (Logisim: {want!r})"))
sys.exit(1 if failures else 0)
