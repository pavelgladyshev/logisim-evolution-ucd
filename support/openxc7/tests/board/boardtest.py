#!/usr/bin/env python3
"""On-board test of a circuit with a Keyboard and a TTY, through Logisim's whole FPGA flow, on a Cmod A7-35T:

  support/openxc7/tests/board/boardtest.py [--reference other.circ] [--seconds S] [--port NAME] \
        <file.circ> <top> <tick Hz> [--] <input> [<input> ...]   (-- before inputs that start with '-')

The circuit's board map is completed automatically (TTY/Keyboard to the UART), then Logisim builds and loads it
(--test-fpga). For each input the bitstream is loaded again (a fresh start, like a new simulation), the input
is typed on the board's USB-UART ('\\n' = Enter, sent as CR like a terminal does), and what comes back must be what
Logisim's own simulation (--tty tty) prints for the same input, shown as on a terminal. --reference simulates
another file (e.g. one without a POR, which the --tty mode does not release); --seconds is the simulation time.

Needs the built jar, a JDK, the toolchain (~/openxc7, or $OPENXC7_DIR) and the board. Runs on macOS, Linux and
Windows; --port names the board's serial port (COM5, /dev/cu.usbserial-…) when it is not found by itself.
"""
import argparse, glob, os, pathlib, re, subprocess, sys, time

HERE = pathlib.Path(__file__).resolve().parent
TESTS = HERE.parent
REPO = TESTS.parents[2]
OUT = TESTS / "build" / "board"
TOOLCHAIN = pathlib.Path(os.environ.get("OPENXC7_DIR", pathlib.Path.home() / "openxc7"))
LOADER = TOOLCHAIN / "bin" / ("openFPGALoader.exe" if os.name == "nt" else "openFPGALoader")
BOARD = "DIGILENT_CMOD_A7_35T"

parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
parser.add_argument("circ", type=pathlib.Path)
parser.add_argument("top")
parser.add_argument("frequency")
parser.add_argument("inputs", nargs="+")
parser.add_argument("--reference", type=pathlib.Path)
parser.add_argument("--seconds", type=float, default=5)
parser.add_argument("--port", help="the board's serial port, instead of looking for it (e.g. COM5)")
args = parser.parse_args()



def jdk_commands():
    """java and javac out of the JDK's own bin, rather than whatever those names reach on PATH.

    On Windows a bare "java" is usually Oracle's javapath shim: it starts the real JVM as its own child
    and exits. reference() below kills what it spawned and then reads to end of file, and with the shim
    in between the kill reaches only the shim - the JVM outlives it, holding the write end of the pipe,
    so the read waits for an EOF that can never arrive and the test hangs after the flow has passed. An
    absolute path makes the JVM the direct child again, which is what the kill and the read already
    assume. Whichever binary the name reached, the JVM it starts reports where it lives, so this needs
    no registry, no JAVA_HOME and no guess about how Java was installed.
    """
    try:
        settings = subprocess.run(["java", "-XshowSettings:properties", "-version"], capture_output=True, text=True)
    except OSError:
        return "java", "javac"                         # no java at all: fail at the compile, as before
    home = re.search(r"java\.home = (.+)", settings.stderr)       # -XshowSettings writes to stderr
    if not home:
        return "java", "javac"
    binaries = pathlib.Path(home.group(1).strip()) / "bin"
    suffix = ".exe" if os.name == "nt" else ""
    return str(binaries / f"java{suffix}"), str(binaries / f"javac{suffix}")


JAVA, JAVAC = jdk_commands()
JAR = sorted((REPO / "build" / "libs").glob("logisim-evolution-*-all.jar"))[0]
CLASSES = TESTS / "build" / "classes"
CLASSES.mkdir(parents=True, exist_ok=True)
subprocess.run([JAVAC, "-nowarn", "-d", str(CLASSES), "-cp", str(JAR), *map(str, TESTS.glob("harness/*/*.java"))],
               check=True)
name = re.sub(r"[^A-Za-z0-9_]", "_", args.circ.stem)
work = OUT / name
work.mkdir(parents=True, exist_ok=True)


def as_property(path):
    """A path as a value for the file below, which isoprefs reads with java.util.Properties, where a backslash
    is an escape character: undoubled, C:\\Users\\pavel arrives as C:Userspavel and the workspace and the tool
    path both end up somewhere else entirely. Harmless where the separator is already a forward slash."""
    return str(path).replace("\\", "\\\\")


prefs = work / "prefs.properties"
prefs.write_text(f"/com/cburch/logisim|FPGAWorkspace={as_property(work / 'ws')}\n"
                 f"/com/cburch/logisim|hdlType=Verilog\n"
                 f"/com/cburch/logisim|OpenXc7ToolPath={as_property(str(LOADER.parent) + os.sep)}\n")


def java(*arguments):
    return [JAVA, "-Djava.util.prefs.PreferencesFactory=isoprefs.FilePrefsFactory", f"-Disoprefs.file={prefs}",
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


# The board's UART, twice: Port opens it at 115200 8N1 with no flow control and reads whatever has arrived,
# and serial_port finds it. Windows has neither termios nor a select that takes anything but a socket, so the
# two have nothing in common below the line they draw here.
if os.name != "nt":
    import select, termios

    class Port:
        def __init__(self, port):
            self.fd = os.open(port, os.O_RDWR | os.O_NOCTTY | os.O_NONBLOCK)
            cc = termios.tcgetattr(self.fd)[6]
            cc[termios.VMIN], cc[termios.VTIME] = 0, 0
            termios.tcsetattr(self.fd, termios.TCSANOW, [0, 0, termios.CS8 | termios.CREAD | termios.CLOCAL, 0,
                                                         termios.B115200, termios.B115200, cc])
            termios.tcflush(self.fd, termios.TCIOFLUSH)

        def write(self, data):
            os.write(self.fd, data)

        def read(self):
            """What has arrived, waiting up to 50 ms for the first of it."""
            return os.read(self.fd, 4096) if select.select([self.fd], [], [], 0.05)[0] else b""

        def close(self):
            os.close(self.fd)

    def serial_port():
        if sys.platform == "darwin":
            ports = sorted(glob.glob("/dev/cu.usbserial-*1"))
        else:
            ports = sorted(glob.glob("/dev/serial/by-id/*Digilent*-if01-port0")) or ["/dev/ttyUSB1"]
        return ports[0]

else:
    import ctypes, winreg

    # The Win32 types these three structures are made of. Fixed-width rather than ctypes.wintypes, whose DWORD
    # is c_ulong: right on Windows and 8 bytes on a 64-bit Unix, which would leave the layout below unverifiable
    # anywhere but the machine it is for. This way sizeof(DCB) is 28 and sizeof(COMMTIMEOUTS) 20 everywhere.
    DWORD, WORD = ctypes.c_uint32, ctypes.c_uint16
    HANDLE, BOOL = ctypes.c_void_p, ctypes.c_int
    INVALID_HANDLE_VALUE = ctypes.c_void_p(-1).value
    GENERIC_READ, GENERIC_WRITE, OPEN_EXISTING = 0x80000000, 0x40000000, 3
    PURGE_TXCLEAR, PURGE_RXCLEAR = 0x0004, 0x0008
    MAXDWORD = 0xFFFFFFFF

    class DCB(ctypes.Structure):
        """The line settings. Its 16 flag bits are one DWORD here because BuildCommDCBW sets them, which is
        the point of using it: the mode string says what the flags should mean and Windows lays them out."""
        _fields_ = [("DCBlength", DWORD), ("BaudRate", DWORD), ("flags", DWORD),
                    ("wReserved", WORD), ("XonLim", WORD), ("XoffLim", WORD),
                    ("ByteSize", ctypes.c_ubyte), ("Parity", ctypes.c_ubyte), ("StopBits", ctypes.c_ubyte),
                    ("XonChar", ctypes.c_char), ("XoffChar", ctypes.c_char), ("ErrorChar", ctypes.c_char),
                    ("EofChar", ctypes.c_char), ("EvtChar", ctypes.c_char), ("wReserved1", WORD)]

    class COMMTIMEOUTS(ctypes.Structure):
        _fields_ = [(field, DWORD) for field in
                    ("ReadIntervalTimeout", "ReadTotalTimeoutMultiplier", "ReadTotalTimeoutConstant",
                     "WriteTotalTimeoutMultiplier", "WriteTotalTimeoutConstant")]

    k32 = ctypes.WinDLL("kernel32", use_last_error=True)
    k32.CreateFileW.restype = HANDLE
    k32.CreateFileW.argtypes = [ctypes.c_wchar_p, DWORD, DWORD, ctypes.c_void_p, DWORD, DWORD, HANDLE]
    for function, arguments in (("GetCommState", [HANDLE, ctypes.POINTER(DCB)]),
                                ("SetCommState", [HANDLE, ctypes.POINTER(DCB)]),
                                ("BuildCommDCBW", [ctypes.c_wchar_p, ctypes.POINTER(DCB)]),
                                ("SetCommTimeouts", [HANDLE, ctypes.POINTER(COMMTIMEOUTS)]),
                                ("PurgeComm", [HANDLE, DWORD]),
                                ("CloseHandle", [HANDLE]),
                                ("WriteFile", [HANDLE, ctypes.c_void_p, DWORD, ctypes.POINTER(DWORD),
                                               ctypes.c_void_p]),
                                ("ReadFile", [HANDLE, ctypes.c_void_p, DWORD, ctypes.POINTER(DWORD),
                                              ctypes.c_void_p])):
        getattr(k32, function).argtypes, getattr(k32, function).restype = arguments, BOOL

    def checked(ok):
        if not ok:
            raise ctypes.WinError(ctypes.get_last_error())

    class Port:
        def __init__(self, port):
            # \\.\COMn: the plain name only reaches COM1 to COM9, and a laptop with Bluetooth is often past that.
            self.handle = k32.CreateFileW(port if port.startswith("\\\\") else rf"\\.\{port}",
                                          GENERIC_READ | GENERIC_WRITE, 0, None, OPEN_EXISTING, 0, None)
            if self.handle in (None, INVALID_HANDLE_VALUE):
                raise ctypes.WinError(ctypes.get_last_error())
            settings = DCB()
            settings.DCBlength = ctypes.sizeof(DCB)
            checked(k32.GetCommState(self.handle, ctypes.byref(settings)))   # also says at once if it is no port
            checked(k32.BuildCommDCBW("baud=115200 parity=N data=8 stop=1", ctypes.byref(settings)))
            settings.DCBlength = ctypes.sizeof(DCB)                          # BuildCommDCBW does not set it
            checked(k32.SetCommState(self.handle, ctypes.byref(settings)))
            # Return with whatever has already arrived and never wait: the documented meaning of an interval of
            # MAXDWORD with both totals zero. read() below sleeps instead, so an empty port costs the same 50 ms
            # as the select does on Unix, and a port with something on it is read as fast as it fills.
            timeouts = COMMTIMEOUTS(MAXDWORD, 0, 0, 0, 0)
            checked(k32.SetCommTimeouts(self.handle, ctypes.byref(timeouts)))
            checked(k32.PurgeComm(self.handle, PURGE_TXCLEAR | PURGE_RXCLEAR))

        def write(self, data):
            buffer, written = ctypes.create_string_buffer(data, len(data)), DWORD(0)
            checked(k32.WriteFile(self.handle, ctypes.byref(buffer), len(data), ctypes.byref(written), None))

        def read(self):
            buffer, got = ctypes.create_string_buffer(4096), DWORD(0)
            checked(k32.ReadFile(self.handle, ctypes.byref(buffer), 4096, ctypes.byref(got), None))
            if not got.value:
                time.sleep(0.05)
            return buffer.raw[:got.value]

        def close(self):
            k32.CloseHandle(self.handle)

    def all_serial_ports():
        try:
            with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"HARDWARE\DEVICEMAP\SERIALCOMM") as ports:
                return [winreg.EnumValue(ports, i)[1] for i in range(winreg.QueryInfoKey(ports)[1])]
        except OSError:
            return []

    def serial_port():
        """The FT2232H on the board has two interfaces: A is the JTAG that openFPGALoader takes and B is this
        UART. So the port wanted is the one whose FTDIBUS key ends in B - the same second interface the branch
        above picks out with -if01- and the trailing 1 - and only an interface bound to the serial driver has a
        PortName at all, which leaves the JTAG side out by itself."""
        ftdi = {}
        try:
            with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"SYSTEM\CurrentControlSet\Enum\FTDIBUS") as bus:
                for index in range(winreg.QueryInfoKey(bus)[0]):
                    device = winreg.EnumKey(bus, index)
                    try:
                        with winreg.OpenKey(bus, rf"{device}\0000\Device Parameters") as parameters:
                            ftdi[device] = winreg.QueryValueEx(parameters, "PortName")[0]
                    except OSError:
                        pass                                  # an interface the serial driver does not hold
        except OSError:
            pass                                              # no FTDI device has ever been attached
        board = [port for device, port in ftdi.items() if "PID_6010" in device and device.endswith("B")]
        if len(board) == 1:
            return board[0]
        if not board and len(all_serial_ports()) == 1:
            print(f"  (no FTDI channel B in the registry; taking the one serial port, {all_serial_ports()[0]})")
            return all_serial_ports()[0]
        sys.exit(f"cannot tell which serial port is the board: name it with --port, e.g. --port COM5.\n"
                 f"  FTDI interfaces with a port: {ftdi or 'none'}\n"
                 f"  all serial ports: {all_serial_ports() or 'none'}")


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

port_name = args.port or serial_port()
failures = 0
for index, raw in enumerate(args.inputs):
    text = raw.encode().decode("unicode_escape")
    if index > 0:
        subprocess.run([str(LOADER), "-b", "cmoda7_35t", str(bitstream)], capture_output=True)
    time.sleep(3)                                      # e.g. for a power-on reset of 2 s
    port = Port(port_name)
    try:
        for byte in text.replace("\n", "\r").encode():
            port.write(bytes([byte]))
            time.sleep(0.02)
        reply, end = b"", time.time() + 2
        while time.time() < end:
            reply += port.read()
    finally:
        port.close()
    got = reply.decode("ascii", "replace").replace("\r\n", "\n")
    want = reference(text)
    ok = got == want
    failures += not ok
    print(f"  {'ok  ' if ok else 'FAIL'} {text!r:26} -> {got!r}" + ("" if ok else f"   (Logisim: {want!r})"))
sys.exit(1 if failures else 0)
