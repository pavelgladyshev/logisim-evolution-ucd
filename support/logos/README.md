# LogOS's computer on an FPGA

The aim is to run [LogOS](https://github.com/pavelgladyshev/logos) on a Sipeed Tang Nano 20K: its computer.circ,
built by Logisim's FPGA flow, with the RV32IM CPU, the RAM in the board's 8 MB SDRAM, the Block Storage on a
microSD card in the board's TF slot, and the TTY and Keyboard on the board's USB serial port. Work in progress:

| Step | State |
| --- | --- |
| 1. A separate data bus for the CPU, Block Storage and Timer | done |
| 2. computer.circ with a multiplexed bus instead of a shared one | done, in Logisim |
| 3. HDL for the Timer, and for the Block Storage on an SD card | to do |
| 4. HDL for the RAM in the SDRAM | to do |
| 5. HDL for the CPU: a Verilog model of the Java one, tick for tick | to do |
| 6. LogOS on the board | to do |

## 1. A separate data bus

An FPGA cannot build a bus that several components drive in turn, as computer.circ's data bus is: the CPU,
the RAM, the Block Storage, the Timer and three Controlled Buffers all drive it, and the CPU and the Block
Storage's DMA share the address, MemRead, MemWrite and byte-enable lines too. So the RV32IM CPU, the Block
Storage and the Timer have an attribute **Data Bus**: *Bidirectional* (the default, as before) or *Separate In
and Out*. With a separate bus a component reads its data on a Data In pin and writes it on a Data Out pin, where
the INOUT pin was, and its outputs drive 0 where the bidirectional ones float:

- **RV32IM CPU**: Data In on the left (with the other inputs). While it has granted the bus to a DMA device
  (BusAck = 1), Address, MemRead, MemWrite, Data Out and the byte enables are 0.
- **Block Storage**: Data Out gives the register that is read and, during a disk read, the words its DMA writes
  to memory; Data In (left) takes the register writes; Memory Data In (right) the words its DMA reads from memory
  during a disk write. The byte enables are outputs, below Bus Request. When it is idle, its DMA outputs are 0.
- **Timer**: Data In on its bottom edge.

## 2. computer.circ with a multiplexed bus

`fpga_bus.py` makes it from LogOS 3.0's computer.circ (branch v3.0-glendalough), as COMP20020's Week 10 BEAGs
and A4 memory choose their data:

```bash
./gradlew shadowJar
support/logos/fpga_bus.py build/libs/logisim-evolution-ucd-5.0.3-all.jar ~/logos/computer.circ computer.circ
```

- Five multiplexers, selected by BusAck, choose who drives Address, the write data (WData), MemRead, MemWrite and
  the byte enables (BE): the CPU, or the Block Storage while it has the bus.
- What the CPU reads, and the DMA reads from memory (RData), comes from one multiplexer whose select is the
  number of the device whose address comparator gives 1, from a priority encoder: 0 ROM, 1 RAM, 2 keyboard
  character, 3 keyboard ready, 4 timer, 5 block storage registers. With no device selected it reads the ROM.
- The Controlled Buffers and Pull Resistors are gone. Every pin of the old shared nets has a tunnel of its own,
  and everything but the CPU moved 150 to the right to make room for the CPU's tunnels.

The script checks its result with Logisim (no net may have two outputs on it), using `NetDump` from
`support/openxc7/tests/harness`; `CircPng` there draws a circuit into a PNG without Logisim's window.

Checked on 4 November 2026 with `logos_session.py`, which boots LogOS without Logisim's window and types at its
shell: LogOS 3.0's ten test programs (fs_test, pipe_test, redir_test, fd_test, proc_test, link_test, shm_test,
sem_test, stress_test, script_test) pass, 81 of 81, in the new circuit as in the old one, the two sessions are
identical and so are the disk images afterwards. The old circuit gives the same session with Logisim 5.0.3 and
with this version, whose CPU, Block Storage and Timer are bidirectional by default.
