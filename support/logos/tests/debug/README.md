# Debugging riscvCpu on an FPGA

With Debug Enable set on the RV32IM CPU, Logisim's FPGA flow gives the CPU the RISC-V External Debug Support
(0.13.2) of `riscvDebug.v`, on the FPGA's own JTAG port. OpenOCD drives it, and gdb connects to OpenOCD as it
does to Logisim's GDB server. The JTAG port is Gowin's (`GW_JTAG`, a GW2A such as the Tang Nano 20K's), reached
through the board's USB cable: no other adapter is needed.

## On the board

```sh
openocd -f tangnano20k.cfg          # leave it running
gdb-multiarch -ex 'target extended-remote :3334' kernel-debug.elf
```

OpenOCD's gdb port is 3334, not its usual 3333: with Debug Enable set, Logisim's own GDB server (for the CPU in
the simulation) listens on the CPU's TCP Port, 3333, whenever the circuit is open, and gdb would reach that
instead of the board.

gdb halts the CPU when it connects. Breakpoints (`break`, which writes an `ebreak` into memory, and `hbreak`, one of
the CPU's two triggers), `step`, `next`, `finish`, registers (`info registers`, including `priv`) and memory all
work. `detach` lets the CPU go on. The board's console keeps working meanwhile: what you type while the CPU is
halted waits for it.

`tangnano20k.cfg` runs `bl616_latency.py` once OpenOCD has started. The board's USB chip, a BL616 that looks
like an FT2232, takes the latency timer of its JTAG interface for its serial port too. OpenOCD sets that timer
to 255 ms, which would hold back the console's short output by up to that long. The script puts it back to 1 ms.
It uses Linux's usbfs (Linux and WSL 2): elsewhere OpenOCD says it could not, and the console is slower.

Two things to do in order. Start OpenOCD a moment after the download, once LogOS has booted: the CPU starts about
0.13 s after the bitstream is loaded, and OpenOCD's first look at it waits less than that for it to halt (it gave up
0.09 s after the load). Started at once, it says "unable to halt hart 0" and quits, but its halt request stays, and the CPU halts
at its second instruction and waits; the next OpenOCD finds it there. And start OpenOCD before you open the console: as it
starts, the BL616 can put a few stray characters into the console's input (`p|||`), and on the first start after the
board was plugged in, with the console open, its serial port has twice stopped altogether, in both directions, until
the board was unplugged. Later starts, with the console open or not, have not stopped it.

## In simulation

`make sim` runs the CPU, a RAM with `prog.c` and a model of `GW_JTAG` (`gw_jtag_model.v`, with what the user's
logic sees on the real chip) in Icarus Verilog. OpenOCD reaches the model through `remote_bitbang` (`rbb_vpi.c`)
and runs gdb's session `gdb_test.gdb` against it. It needs iverilog, OpenOCD, gdb-multiarch and a RISC-V gcc.

The CPU with DEBUG and no debugger must do exactly what it does without: `../cpu/tb_cpu_replay.v` checks it
against a record of the Java CPU (instantiate the CPU with `.DEBUG(1)` and add `gw_jtag_model.v` and
`riscvDebug.v`).
