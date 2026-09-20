# Logisim-evolution on an FPGA with openXC7 (Digilent Cmod A7)

This version of Logisim-evolution 5.0.0 builds a circuit and loads it into a Xilinx 7-series FPGA board, such as the
**Digilent Cmod A7-35T**, straight from its **FPGA** menu, with the open-source **openXC7** toolchain instead of
Vivado. It runs on macOS (Apple Silicon or Intel) and on Linux (e.g. Kubuntu 24.04). A circuit goes from the
schematic to running on the board in about 10–30 seconds.

The toolchain is:

- [Yosys](https://github.com/YosysHQ/yosys) for synthesis
- [nextpnr-xilinx](https://github.com/openXC7/nextpnr-xilinx) for place and route
- [Project X-Ray](https://github.com/f4pga/prjxray)'s `fasm2frames` and `xc7frames2bit` for the bitstream
- [openFPGALoader](https://github.com/trabucayre/openFPGALoader) to load the bitstream over USB

## 1. Installation

The scripts are in `support/openxc7/` of this repository, and at the top of the zip for other computers.
`setup_toolchain.sh` builds the toolchain into `~/openxc7`, which Logisim finds by itself. It uses about 0.5 GB on
macOS and about 1.3 GB on Linux, where Yosys is built from source as well. Most of it is in directories that look
like build trees, and only `yosys-src/` really is one: `nextpnr-xilinx/` and `prjxray/` hold the place-and-route
binary, the Project X-Ray database and the script that `fasm2frames` runs, which is why `bin/` is 20 KB of symlinks
and wrappers pointing into them. If disk is short, delete `yosys-src/` — half a gigabyte nothing refers to, and
re-running the script will not rebuild it, because the check is for the installed `yosys/`.
`install_logisim.sh` copies Logisim to `~/openxc7/logisim` and adds a launcher. The zip carries a ready-made jar;
from a clone, build one first with `./gradlew shadowJar`, which needs a JDK (`openjdk-21-jdk` on Ubuntu), not just
the JRE below.

### macOS

```bash
brew install yosys boost eigen openfpgaloader cmake python@3.13 git
support/openxc7/setup_toolchain.sh
support/openxc7/install_logisim.sh
```

Logisim needs Java 21 or newer (e.g. Amazon Corretto 21 or Temurin 21). The launcher is
`~/Applications/Logisim-evolution openXC7.app`.

### Kubuntu / Ubuntu 24.04

```bash
sudo apt install git cmake build-essential pkg-config python3 python3-venv openfpgaloader \
     libboost-filesystem-dev libboost-thread-dev libboost-program-options-dev libboost-iostreams-dev \
     libboost-system-dev libeigen3-dev bison flex gawk libreadline-dev tcl-dev libffi-dev zlib1g-dev \
     openjdk-21-jre picocom
./setup_toolchain.sh
./install_logisim.sh
```

On Linux the setup also builds Yosys 0.68, because Ubuntu's Yosys 0.33 is too old. That build uses CMake and
needs CMake 3.28 or newer, which is what 24.04 has. Allow 20–30 minutes for the whole build (11 minutes on a
16-core laptop). The `openfpgaloader` package brings the udev rule that gives the desktop user access to the
board's USB programmer and serial port; if the board was already plugged in and is not found, **unplug it and
plug it in again**. Logisim appears in the applications menu
as *Logisim-evolution (openXC7)*.

## 2. In class: from a circuit to the board

1. Open the circuit and choose **FPGA → Synthesize & Download…**. This opens the FPGA Commander.
2. Choose the board **DIGILENT_CMOD_A7_35T** and the top-level circuit (**Toplevel**).
3. Set the **frequency**. It is the tick frequency, as in *Simulate → Tick Frequency*: a clock with default
   settings completes one cycle every two ticks. The maximum on the Cmod A7 is 3 MHz, a quarter of its 12 MHz
   clock. A few Hz lets students watch LEDs change; use 1 MHz for a processor. The list holds Logisim's tick
   frequencies, so there is no 1 MHz entry: the board divides its 12 MHz by a whole number, and **1024 kHz**
   gives exactly 1000 kHz (divider 6).
4. Press **Annotate** if any component in the circuit has no label; the A4 BEAG solution is one such circuit.
   Without it the run stops at once with *Found one or more components without a label*, because the generated
   hardware needs a name for each of them. Leave the choice above the button on *Label only the components
   without a label…*: *Relabel all components* renames the ones that have labels too, and a map already saved
   for the circuit refers to them by name.
5. Choose **Synthesize & Download** and press **Execute**.
6. The first time, Logisim asks you to **map** each I/O component to a board resource. Pick the component in the
   list, then click the resource on the board picture:
   - buttons → **BTN0**/**BTN1**
   - LEDs → **LED1**/**LED2**, or the RGB LED **LED0**
   - **TTY → UART_TX** and **Keyboard → UART_RX**, the two boxes at the USB connector
   - pins, DIP switches and 7-segment displays → the header pins (**PIO…**), for hardware wired to the board
   - anything not needed → *constant* (inputs) or *open* (outputs)

   Save the circuit, and the map is kept for next time. Maps are stored per board, so the same map serves whether
   the board is built with openXC7 or with Vivado.
7. The console shows the steps, then `Load SRAM … 100%`: the circuit runs on the board until it loses power.

For circuits with a TTY or a Keyboard, open a terminal on the board's serial port (115200 baud):

```bash
support/openxc7/uart_terminal.sh
```

This uses picocom (quit: Ctrl-A Ctrl-X) or screen (quit: Ctrl-A K). What the circuit prints appears there, and what
you type goes to its Keyboard. Leave it open while you load the circuit again: programming and the serial port are
separate interfaces of the board's USB chip.

### Settings

**Preferences → Software** has the openXC7 tool path (default `~/openxc7/bin/`) and the switch *Use openXC7 instead
of Vivado for Xilinx 7-series boards*. The switch is on by default whenever `~/openxc7/bin` exists, and keeps
whatever you last set it to once you have touched the tick box. openXC7 reads Verilog, so Logisim changes the HDL
type to Verilog when it uses openXC7.

## 3. How the FPGA version behaves

The board behaves like a simulation. The Logisim clock runs at the chosen frequency, and all flip-flops, registers,
counters and RAM start at 0, as in a new simulation.

**TTY** becomes a serial transmitter (115200 baud, 8N1), and the terminal shows what the TTY shows:

- Printable characters are sent as they are.
- A new line (`\n` or `\r`) is sent as CR LF.
- Backspace erases the last character.
- A form feed, or a rising *clear* input, clears the screen.
- Other control characters are ignored, as the TTY component ignores them.
- An unconnected write enable writes at every clock edge, as in Logisim.

Characters wait in a queue of 1024 while the line sends them, about 11,500 characters a second. A circuit that
prints faster than that for longer loses characters. The terminal wraps long lines at its own width, not at the
TTY's.

**Keyboard** becomes a serial receiver:

- Enter arrives as `\n` and Backspace as `\b`, as when typing into the Keyboard component.
- The buffer length attribute is honoured.
- An unconnected read enable takes a character at every clock edge, as in Logisim.

**POR** (power-on reset) gives its start value for its configured time after the board is loaded, then its end
value. It needs a clocked component in its own circuit to time it.

### What cannot go onto the FPGA

Logisim refuses these, and its message names the components:

- **Tri-state buses**: several Controlled Buffers driving one wire ("net with multiple drivers"), as in the
  Week 9/10 BEAGs. Use a multiplexer instead.
- **Transistors and pull resistors**, as in the transistor-level ROMs and RAMs.
- **VHDL components** (VHDL entities), such as a hand-written VHDL UART, because openXC7 reads Verilog. Use the TTY
  and Keyboard instead.
- A top level with **no pins, LEDs, buttons, TTY or Keyboard**: nothing on it would be visible on the board.

The design must also meet the board's clock: the console prints nextpnr's
`Max frequency for clock … (PASS at 12.00 MHz)`. A FAIL means the combinational logic between registers is too
deep for 12 MHz.

## 4. Troubleshooting

| Message or problem | What to do |
|---|---|
| `No nextpnr-xilinx chip database for <part>` | Make it once: `~/openxc7/bin/openxc7-chipdb <part>`, e.g. `xc7a15tcpg236-1` for the Cmod A7-15T. A ready-made one from a *stable* release of [FPGAwars/tools-openxc7](https://github.com/FPGAwars/tools-openxc7/releases) does as well, if it was built against the nextpnr revision in `setup_toolchain.sh`: the database depends only on its input data, so theirs and ours are then the same file. |
| The board is not found when loading | Check the USB cable. On Linux, unplug and plug in the board after installing `openfpgaloader`. `openFPGALoader -b cmoda7_35t --detect` shows the FPGA it finds on the board. Without `-b` it reads the JTAG chain with a generic pinout and finds nothing on a Cmod A7, so look for an `idcode` in the output rather than at the exit code, which is not a reliable check. |
| Logisim seems to stall at `Loading file…` | Not openXC7: a prompt about an autosave, left behind by a Logisim that was killed, can sit behind the splash screen. Alt-tab to it and answer it. |
| No FPGA actions in the FPGA Commander | Set the tool path under *Preferences → Software → openXC7*. |
| Characters missing in the terminal | The circuit prints faster than 115200 baud for longer than the queue lasts. Lower the frequency. |
| Want Vivado instead | Untick *Use openXC7 instead of Vivado…* in *Preferences → Software*. |

## 5. What is changed in Logisim

New:

- The openXC7 flow (`fpga/download/OpenXc7Download.java`) and its preference.
- A **DIGILENT_CMOD_A7_35T** board, with the UART as two mappable resources.
- FPGA versions of the **TTY** and **Keyboard** (UART), and of **POR**. The **Divider** can go onto an FPGA too:
  in 5.0.0 it had no HDL.

Fixed, because the FPGA did not behave like the simulation:

- **Multiplier**: the upper half of the product was wrong in every mode, and its VHDL had syntax errors. A 1-bit
  Multiplier or Divider gave invalid VHDL.
- **Shifter**: in Verilog wrong for most shift amounts, and for rotates and arithmetic shifts; in VHDL wrong for
  arithmetic shifts.
- **Subtractor** (Verilog): the borrow out was inverted.
- **Counter**: an unconnected up/down input counted down; a loaded value above the maximum was not masked; the
  carry was set while clearing; counters of different widths shared a module with the first counter's maximum
  width.
- **RAM**: the output enable (*ld*) was ignored, or an unconnected one disabled the output. The line-enable RAM
  (as in BEAG) read the wrong data.
- **Gates** (Verilog): used the SystemVerilog keyword `bit`. **Arrays** (Verilog) used a SystemVerilog `typedef`.
- Registers, shift registers and memories now also start at 0 in HDL simulation, in both languages, as they do
  in Logisim and in the FPGA.

Friendlier:

- DRC messages name the components they are about.
- Pins without an I/O standard get LVCMOS33, and the Cmod A7's analog pins got one.
- A relative workspace path works.

## 6. Tests

`tests/` compares Logisim's own simulation with the generated hardware. See `tests/README.md`. When this was
written:

- **Components**: 96 configurations of arithmetic, gate, plexer, wiring and memory components match the
  simulation exactly, over 4000 random and edge-case inputs or clock cycles each. Unit tests of the UART TTY and
  Keyboard (bursts, control characters, full queues) pass.
- **Course circuits**: all 31 of the 44 COMP20020 2026/27 circuits that can go onto an FPGA build to a bitstream.
  Of the other 13, the Logisim toys were excluded; the rest are the kinds listed in section 3.
- **Board**: the Week 7 hex code printer, the A2 ROT13 exercise (with its ROM filled) and the A4 BEAG solution,
  run on a Cmod A7-35T, print exactly what Logisim's simulation prints for the same keyboard input.
- **VHDL** (used by the Vivado flow): the same 96 configurations and the UART unit tests also pass in VHDL,
  in GHDL.
