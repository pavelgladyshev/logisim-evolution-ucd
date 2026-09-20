# Tests: does the FPGA do what Logisim's simulation does?

Three kinds of tests. Each compares Logisim's own simulation with the hardware that Logisim generates. They keep
their own Logisim preferences (in `build/`), so your settings are not touched. They need the built jar
(`./gradlew shadowJar`), a JDK and python3.

## Components: `run_component_tests.sh`

```bash
support/openxc7/tests/run_component_tests.sh            # or: ... arith comb seq io
```

This also needs Icarus Verilog (`brew install icarus-verilog`, `sudo apt install iverilog`). Each line of
`specs/*.txt` is a component with attributes, for example `cnt4stay Counter width=4 max=0x9 ongoal=stay clock=2
rare=3`. `harness/lstest/HdlDiff.java` places the components in a circuit, with a pin on every port. Clock inputs
are driven by a Clock, and *rare* inputs such as clears are 1 in only one step of 16. The same random and edge-case
inputs then go through Logisim's simulator and through the generated Verilog. For clocked components, each step
is: set the inputs, rising edge, falling edge, read the outputs.

`compare.py` must report `0 mismatches`. `trace.py build/run_<spec> <name>` shows one component step by step.
The `io` test is a testbench (`tb_io.v`) for the UART TTY and Keyboard.

With `--vhdl` the same tests run on the VHDL that Logisim generates, in GHDL: `harness/lstest/HdlDiff.java`
writes a VHDL testbench (`tb.vhd`) for the same inputs, and `tb_io.vhd` is the VHDL version of the UART test.
Both languages must give Logisim's results. GHDL comes from `sudo apt install ghdl` on Ubuntu; on macOS the
Homebrew package is disabled, so unpack the release from https://github.com/ghdl/ghdl/releases into
`~/openxc7/ghdl`, where the script also looks (or set `GHDL`).

Only the VHDL run reports dropped characters in the TTY burst test (`441 characters dropped when the queue was
full`), because the two testbenches count them differently: `tb_io.v` reads the TTY's own queue-full flag and
never expects a character the queue refused, while `tb_io.vhd` cannot look inside the component and counts every
character that never arrived. The two runs send the same number of bytes, which is what says the two TTYs behave
alike.

## Course circuits: `run_course_test.py`

```bash
support/openxc7/tests/run_course_test.py ~/git/COMP20020/2026_2027_course
```

For every circuit, this completes the board map automatically (`harness/lstest/AutoMap.java`):

- TTY and Keyboard go to the UART.
- 1-bit inputs and outputs go to the buttons and LEDs.
- Everything else goes to the header pins, or to constants when the pins run out.

It then runs Logisim's whole FPGA flow up to the bitstream. The board is not touched: a stand-in for openFPGALoader
only checks that the bitstream is there. Circuits under `Logisim_toys` are skipped (`--exclude`). The table lists
each circuit's result and its maximum clock frequency, and `build/course/<circuit>/` keeps its logs.

## On the board: `board/boardtest.py`

For circuits with a Keyboard and a TTY, with a Cmod A7-35T connected:

```bash
T=support/openxc7/tests; C=~/git/COMP20020/2026_2027_course
$T/board/boardtest.py $C/Assignments/A4/solutions/a4_beag_solution.circ beag 1000000 -- '-15\n' '32767\n'
```

This builds and loads the circuit, types each input on the board's serial port, and compares the reply with
Logisim's own simulation of the same input (`--tty tty`). Logisim's `--tty` mode never ends a power-on reset, so for
a circuit with a POR give a copy without it with `--reference`. `board/rot13rom.py` fills the ROM of the A2 ROT13
exercise, so that the exercise can be tested.
