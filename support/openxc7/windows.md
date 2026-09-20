# The Windows toolchain, as assembled and proven

Windows has no `setup_toolchain.sh`. Nothing is built from source: Logisim is packaged as an MSI by
`./gradlew.bat createMsi`, and the toolchain is two prebuilt archives unpacked into one folder. This file
records what that folder must contain and why, because two of the details are load-bearing and silent.

This is not yet a student handout. What is proven and what is not is at the end.

## The two artefacts

**FPGAwars tools-openxc7**, stable release `2026-09-15`, `apio-openxc7-windows-amd64`. Supplies
`nextpnr-xilinx.exe`, `xc7frames2bit.exe`, `fasm2frames.cmd` and the Python trees they need. Unpack it so that
*its* `bin` becomes `%USERPROFILE%\openxc7\bin`: `fasm2frames.cmd` computes its package root as `%~dp0..`, so it
only works from its own layout.

The chip database is a separate asset of the same release. On disk it is `xc7a35tcpg236.bin` and Logisim asks for
`xc7a35tcpg236-1.bin`, because apio keys parts by base part plus speed grade while naming the file without it —
rename it into `%USERPROFILE%\openxc7\chipdb\`. It is the same file the Unix script builds:

    ca3a95cd1061bc49c9c789417362191d6c9a4bbb660634e3672f7c82ab45e937   92,996,364 bytes

Their release also publishes a checksum list covering every asset, so these can be verified against upstream.

**oss-cad-suite**, release tag `2026-03-24`, the Windows x64 archive. Supplies `yosys.exe` and
`openFPGALoader.exe` — openFPGALoader has no Windows release of its own, and its own project's Windows assets are
MSYS2 packages rather than anything that can be dropped in a folder.

    111238a7e52892c561f23bb1e19c197f750a09688aa12787ead7a407e0750476   330,628,408 bytes
    Yosys 0.63+173, git sha1 66306a8ca-dirty, x86_64-w64-mingw32-g++ 13.2.1 -O3

That tag is the one FPGAwars validated their package against, which is why it is pinned rather than the current
release: an old Yosys announces itself, while an untested Yosys-against-nextpnr pairing can produce a bitstream
that builds and misbehaves. 0.63 is above this project's own floor of 0.40 (`setup_toolchain.sh`), and it is
settled by outcome below rather than by version number. YosysHQ publish no checksum file for that release, so the
hash above is ours and is the only record of what was taken.

Yosys needs more than its executable: `yosys-abc.exe` is spawned as a separate process by `synth_xilinx`,
`yosys-filterlib.exe` is needed too, and `share\yosys` is a 24 MB data directory. With all of it in place a real
`synth_xilinx` completes under a PATH holding nothing but `system32` and python. `bin` is then 427 MB and the
whole tree 1.12 GB.

## The DLL merge, which is the trap

Four DLL names exist in both archives — FPGAwars ships them in its `bin`, oss-cad-suite in its `lib`:

| | FPGAwars | oss-cad-suite |
| --- | --- | --- |
| `libgcc_s_seh-1.dll` | 171,261 | 735,844 |
| `libpython3.11.dll` | 5,865,999 | 25,544,418 |
| `libstdc++-6.dll` | 4,583,579 | 26,139,876 |
| `libwinpthread-1.dll` | 94,077 | 68,488 |

**oss-cad-suite's versions must win.** Logisim spawns each tool by full path and the child inherits Logisim's
environment, so a PATH-based answer is no answer; the DLLs have to sit beside the executables, where Windows
looks first. One shared set serves all five tools, but it has to be that set.

With FPGAwars' four in place, yosys dies on a real circuit in the synthesis stage:

    terminate called after throwing an instance of 'std::system_error'
      what():  Invalid argument

exit 3, no output — a MinGW threading failure, which is what `libwinpthread` provides. The controlled test, same
script and same input with only those four files differing: FPGAwars' four exit 3 and write nothing;
oss-cad-suite's four exit 0 and write a 12.4 MB `toplevel.json`. nextpnr does not object to loading the 25.5 MB
`libpython3.11` rather than the 5.9 MB one it was built against, proven by place and route completing on a real
design and the board taking the bitstream.

**A trivial module synthesises correctly under either set.** `synth_xilinx` on a two-input AND gate writes JSON
and exits 0 both ways. Only a real circuit separates them, so a smoke test cannot catch this and the unpack order
decides it: unpack FPGAwars first and copy oss-cad-suite's DLLs over it. Doing it the other way round, or
"repairing" it by restoring what looks like the matching runtime, produces a toolchain that passes every quick
check and cannot synthesise anything real.

Project X-Ray's database is expected beside `chipdb`. Their package keeps it at `share\nextpnr\external\prjxray-db`,
so make `%USERPROFILE%\openxc7\prjxray-db` a junction to it (`mklink /J`) or copy it.

## The path is load-bearing twice

`%USERPROFILE%\openxc7\bin` is not a convention. `AppPreferences.defaultOpenXc7ToolPath` returns
`user.home\openxc7\bin` only when that is a directory and the empty string otherwise, and `OpenXc7ForXilinx`
defaults to that string being non-empty — so the openXC7 switch turns itself on from the existence of that one
folder. Lay the tree down anywhere else and `VendorSoftware.getToolchain` returns Vivado for an xc7 part, and
Logisim hunts for `vivado.bat` with nothing pointing at the real problem. (The other reason is `fasm2frames.cmd`
and `%~dp0..`, above.) Nothing else needed configuring: the tool path resolved, the switch enabled itself, and
`fasm2frames.cmd` was invoked in the flow and produced frames.

## Logisim itself

`./gradlew.bat createMsi` with `JAVA_HOME` pointing at a JDK 21. It needs WiX 3.14, which jpackage drives through
`candle.exe` and `light.exe`. **Do not install WiX through winget**: it requires administrator rights and declares
a dependency on the NetFx3 Windows feature, which is not enabled by default on Windows 11 and is itself elevated
and Windows-Update-backed. WiX publish a binaries-only zip of the identical build, 3.14.1.8722 — unzip it and put
it on PATH for the build. `createMsi` then takes 3 minutes 13 seconds and produces a 75.1 MB
`logisim-evolution-5.0.0-amd64.msi`.

The board's JTAG interface (`MI_00` of the FTDI composite device) has to be moved to WinUSB with Zadig. Its UART
interface (`MI_01`) is a COM port and must be left alone: taking WinUSB to it deletes the COM port and there is
nothing left to open.

## What is proven, and what is not

Proven on the board, on a developer machine: the whole flow end to end in 26.4 seconds, all five stages reporting
in order, `Load SRAM` to 100 percent. The A4 BEAG solution answers `-16` to `-15` and `32766` to `32767`, which is
what macOS and Linux both give. That settles Yosys 0.63 by outcome, and makes the version skew across the three
platforms a footnote rather than a risk.

`support/openxc7/tests/board/boardtest.py` does **not** run on Windows — it imports `termios` at module scope,
selects on a serial file descriptor, globs the two Unix device paths, and builds the loader path without an
executable suffix. The gate it defines is still reachable by driving `AutoMap` and `Main` directly and opening the
port at 115200, which is how the numbers above were obtained, but the script itself would need porting.

Not proven, and not provable on a machine that already has a JDK, two Pythons, WSL and a vendor FTDI stack:
SmartScreen on an unsigned MSI on a machine with no JDK in its history, a first board plug with no FTDI driver in
the store, and whether Windows' `python` App Execution Alias stands in for a real interpreter. The MSI has not yet
been installed even here. Those belong to a clean virtual machine, and a developer box quietly passing them is
worse than not testing them, because it looks like evidence.
