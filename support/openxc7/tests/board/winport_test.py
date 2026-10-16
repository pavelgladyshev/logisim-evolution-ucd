"""Run the Windows branch of boardtest.py without Windows: real ctypes for the structures, a stub for kernel32,
and a simulated registry, so the registry walk and the struct layouts are exercised rather than assumed."""
import ctypes, pathlib, sys, textwrap, time, types

SOURCE = pathlib.Path(__file__).resolve().parent / "boardtest.py"
lines = SOURCE.read_text().splitlines(keepends=True)
start = next(i for i, line in enumerate(lines) if line.rstrip() == "else:") + 1
end = next(i for i in range(start, len(lines)) if lines[i].strip() and not lines[i][0].isspace())
block = textwrap.dedent("".join(lines[start:end]))

REGISTRY = {}

class Key:
    def __init__(self, path): self.path = path
    def __enter__(self): return self
    def __exit__(self, *e): return False

def OpenKey(root, sub):
    path = sub if root == "HKLM" else f"{root.path}\\{sub}"
    if path not in REGISTRY: raise OSError(2, "not found", path)
    return Key(path)

def QueryInfoKey(key):
    children = {p[len(key.path) + 1:].split("\\")[0] for p in REGISTRY if p.startswith(key.path + "\\")}
    return len(children), len(REGISTRY[key.path]), 0

def EnumKey(key, i):
    return sorted({p[len(key.path) + 1:].split("\\")[0] for p in REGISTRY if p.startswith(key.path + "\\")})[i]

def EnumValue(key, i): return list(REGISTRY[key.path].items())[i] + (1,)

def QueryValueEx(key, value):
    if value not in REGISTRY[key.path]: raise OSError(2, "no value", value)
    return REGISTRY[key.path][value], 1

winreg = types.ModuleType("winreg")
winreg.HKEY_LOCAL_MACHINE = "HKLM"
for f in (OpenKey, QueryInfoKey, EnumKey, EnumValue, QueryValueEx): setattr(winreg, f.__name__, f)
sys.modules["winreg"] = winreg
ctypes.WinDLL = lambda *a, **k: types.SimpleNamespace(**{n: types.SimpleNamespace() for n in
    ("CreateFileW", "GetCommState", "SetCommState", "BuildCommDCBW", "SetCommTimeouts", "PurgeComm",
     "CloseHandle", "WriteFile", "ReadFile")})

namespace = {"ctypes": ctypes, "time": time, "sys": sys, "print": print}
exec(compile(block, str(SOURCE), "exec"), namespace)
print("structs:  sizeof(DCB) =", ctypes.sizeof(namespace["DCB"]),
      " sizeof(COMMTIMEOUTS) =", ctypes.sizeof(namespace["COMMTIMEOUTS"]))
serial_port = namespace["serial_port"]

FTDI = "SYSTEM\\CurrentControlSet\\Enum\\FTDIBUS"
SERIALCOMM = "HARDWARE\\DEVICEMAP\\SERIALCOMM"

failures = 0


def case(label, registry, expect):
    global REGISTRY, failures
    REGISTRY = registry
    try:
        got = serial_port()
    except SystemExit as e:
        got = f"exit: {str(e).splitlines()[0]}"
    ok = got.startswith(expect) if expect.startswith("exit") else got == expect
    failures += not ok
    print(f"  {'ok  ' if ok else 'FAIL'} {label:38} -> {got}")

board = "VID_0403+PID_6010+210328B0B6D1"
case("board attached (A=JTAG, B=UART)", {
    FTDI: {}, f"{FTDI}\\{board}A": {}, f"{FTDI}\\{board}A\\0000": {},
    f"{FTDI}\\{board}B": {}, f"{FTDI}\\{board}B\\0000": {},
    f"{FTDI}\\{board}B\\0000\\Device Parameters": {"PortName": "COM5"},
    SERIALCOMM: {"\\Device\\BthModem0": "COM3", "\\Device\\VCP0": "COM5"}}, "COM5")

case("both channels bound to the serial driver", {
    FTDI: {}, f"{FTDI}\\{board}A": {}, f"{FTDI}\\{board}A\\0000": {},
    f"{FTDI}\\{board}A\\0000\\Device Parameters": {"PortName": "COM4"},
    f"{FTDI}\\{board}B": {}, f"{FTDI}\\{board}B\\0000": {},
    f"{FTDI}\\{board}B\\0000\\Device Parameters": {"PortName": "COM5"},
    SERIALCOMM: {"\\Device\\VCP0": "COM4", "\\Device\\VCP1": "COM5"}}, "COM5")

case("no FTDIBUS key, one serial port", {SERIALCOMM: {"\\Device\\VCP0": "COM7"}}, "COM7")
case("no FTDIBUS key, two serial ports",
     {SERIALCOMM: {"\\Device\\BthModem0": "COM3", "\\Device\\VCP0": "COM7"}}, "exit: cannot tell")
case("nothing attached at all", {SERIALCOMM: {}}, "exit: cannot tell")

case("two boards plugged in", {
    FTDI: {}, f"{FTDI}\\{board}B": {}, f"{FTDI}\\{board}B\\0000": {},
    f"{FTDI}\\{board}B\\0000\\Device Parameters": {"PortName": "COM5"},
    f"{FTDI}\\VID_0403+PID_6010+999999B": {}, f"{FTDI}\\VID_0403+PID_6010+999999B\\0000": {},
    f"{FTDI}\\VID_0403+PID_6010+999999B\\0000\\Device Parameters": {"PortName": "COM9"},
    SERIALCOMM: {"\\Device\\VCP0": "COM5", "\\Device\\VCP1": "COM9"}}, "exit: cannot tell")

case("a different FTDI device (single channel)", {
    FTDI: {}, f"{FTDI}\\VID_0403+PID_6001+A50285BI": {}, f"{FTDI}\\VID_0403+PID_6001+A50285BI\\0000": {},
    f"{FTDI}\\VID_0403+PID_6001+A50285BI\\0000\\Device Parameters": {"PortName": "COM8"},
    SERIALCOMM: {"\\Device\\VCP0": "COM8"}}, "COM8")   # the fallback, and it says so

sys.exit(1 if failures else 0)
