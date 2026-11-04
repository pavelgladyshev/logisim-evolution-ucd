# LogOS's computer on an FPGA

The aim is to run [LogOS](https://github.com/pavelgladyshev/logos) on a Sipeed Tang Nano 20K: its computer.circ,
built by Logisim's FPGA flow, with the RV32IM CPU, the RAM in the board's 8 MB SDRAM, the Block Storage on a
microSD card in the board's TF slot, and the TTY and Keyboard on the board's USB serial port. The steps:

| Step | State |
| --- | --- |
| 1. A separate data bus for the CPU, Block Storage and Timer | done |
| 2. computer.circ with a multiplexed bus instead of a shared one | done, in Logisim |
| 3. HDL for the Timer, and for the Block Storage on an SD card | done, on the board |
| 4. HDL for the RAM in the SDRAM | done, on the board |
| 5. HDL for the CPU: a Verilog model of the Java one, tick for tick | done, in simulation: LogOS edge for edge |
| 6. LogOS on the board | done: its 81 tests pass, with Logisim's ticks of 2048 kHz |

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

## 3. HDL for the Timer and the Block Storage

Both are Verilog only, and need a separate data bus. Like the CPU they run on the FPGA's clock and act at a
Logisim clock tick, as the Java components do at a rising edge.

- **Timer**: mtime and mtimecmp at the addresses of its attributes, as the Java one. `HdlDiff` (in
  `support/openxc7/tests/harness`) compares the two with random inputs, from `support/openxc7/tests/specs/riscv.txt`.
- **Block Storage**: a microSD card in SPI mode, where the component has its image file: block n of the disk is
  block n of the card, so a disk image goes onto the card as it is (from its block 0, not into a partition). The
  card is on four pins that the board's map gives it: SD_MISO, SD_SCK, SD_MOSI and SD_CS (on the Tang Nano 20K the
  TF slot's TF_D0, TF_CLK, TF_CMD and TF_D3). Its registers and DMA are those of the Java component; a read or write
  of the card takes about a millisecond, during which the status says busy. If the card does not answer, the status
  is 3 (busy and error) and the result 1, as in the Java component when the image file fails; the next command
  starts the card again. It works with SDSC and SDHC cards (SD version 1 and 2). The module that does it,
  `riscvSdBlockStorage`, is in `src/main/resources/resources/logisim/hdl/riscv`.

The Block Storage's tests are in `tests/sd`. `tb_sd.v` runs it against `sd_card_model.v`, a card in SPI mode,
with the CPU's side of the bus in the testbench; it reads, writes and reads back blocks, and checks the errors:

```bash
for cfg in "-DHC=1 -DV2=1" "-DHC=0 -DV2=1" "-DHC=0 -DV2=0" "-DPRESENT=0"; do
  iverilog $cfg -o /tmp/tb_sd support/logos/tests/sd/tb_sd.v support/logos/tests/sd/sd_card_model.v \
    src/main/resources/resources/logisim/hdl/riscv/riscvSdBlockStorage.v && vvp -n /tmp/tb_sd | tail -2
done
```

`tests/sd/board/run.py [block]` builds `sd_board_test.v` for the Tang Nano 20K, with a state machine in place of
the CPU, loads it and prints what the board sends: the status and the card's block (block 0 unless given) in hex.
It writes nothing to the card. Checked on 4 November 2026 with a 2 GB SDSC card formatted FAT16 by a Mac: block 0
is its partition table and block 135 the boot sector of its FAT16 partition, as on the card.

## 4. The RAM in the SDRAM

The RAM has an attribute **FPGA memory**: *Block RAM* (the default, as before) or *The board's SDRAM*. With SDRAM,
the FPGA flow puts the RAM in an SDR SDRAM of 2M words of 32 bits that the board has, and the RAM is mapped to it
like the TTY or the Block Storage: the Tang Nano 20K's board file has an **SDRAM** resource on its FPGA chip, the
8 MB inside the GW2AR-18, whose 55 pins are given by their IO names (as `IOR11B`; nextpnr places them as it does
the pins it calls `O_sdram_*`, checked by `tests/sdram/board/run.py --compare`).

The SDRAM does what the RAM does in Logisim with an asynchronous read and a separate data bus, for up to 21 address
bits and 32 data bits: a bus cycle from one tick to the next reads the word at the address one FPGA clock after the
tick (it has the word 4 clocks after the tick), and then writes the enabled bytes; a refresh between two bus cycles
can delay one by up to 3 clocks. So the RAM's clock needs 12 FPGA clocks a cycle at least, which every tick
frequency of Logisim's gives on the Tang Nano 20K (27 MHz, ticks up to 4 MHz, two ticks a clock cycle). After the
FPGA starts, the SDRAM is started and cleared, since Logisim's RAM starts with 0s; the ticks of a design with a RAM
in the SDRAM start 155 ms after the FPGA. The controller, `logisimSdram`, is in
`src/main/resources/resources/logisim/hdl/memory` and says how it works.

Two older bugs of the RAM's HDL came up on the way, and are fixed for the block RAM too: the HDL took byte i's
enable from the pin BE(n-1-i) instead of BE i, so byte writes went to the other end of the word; and with an
asynchronous read, the output enable and the byte enables of the bus cycle before decided what the RAM gave (with a
CPU, a word read after a byte write showed stale bytes). The HDL now reads whole words, as the simulation does.
`support/openxc7/tests/specs/seq.txt` has RAMs with byte enables now.

The tests are in `tests/sdram`. `tb_sdram.v` runs bus cycles of a CPU (random addresses, reads, writes, byte
enables) on the controller and `sdram_model.v`, an SDRAM that checks the start-up, the timing, the open rows and
the refreshes, and checks each word read against Logisim's RAM; with `-DGENERATED=<module>` it takes the RAM's
module that Logisim generates instead (`-DBLOCK_RAM` for a RAM in block RAM):

```bash
for period in 10 12 16 26 52; do
  iverilog -DPERIOD=$period -o /tmp/tb_sdram support/logos/tests/sdram/tb_sdram.v \
    support/logos/tests/sdram/sdram_model.v src/main/resources/resources/logisim/hdl/memory/logisimSdram.v &&
  vvp -n /tmp/tb_sdram | tail -3
done
```

`tests/sdram/board/run.py` builds `sdram_board_test.v` for the Tang Nano 20K, a state machine in place of the CPU
that does a bus cycle every 12 FPGA clocks over the whole 8 MB, in passes (zeros after the start, a pattern written
and read, then a byte of each word written alone and the words read), and prints a line per pass. Checked on
4 November 2026: 6 passes in 25 seconds, no word wrong. A RAM of computer.circ's size (18 address bits) in the
SDRAM, generated by Logisim's FPGA flow, passes `tb_sdram.v`, and its project for the Tang Nano 20K synthesizes and
places with the SDRAM's pins where they belong.

## 5. The CPU

The Verilog is to do, tick for tick, what the Java CPU does, so that the two can be checked against each other.
Two things came first.

- **TLB Entries**, an attribute of the CPU: 16, 32, 64 or 128 (the default, as before). An FPGA has room for 16, and
  `fpga_bus.py` gives computer.circ's CPU 16, in Logisim too, so that both hit and miss the same: with fewer than
  128, the TLB searches its entries in order, as hardware does. With 16, LogOS's ten test programs have 10398 TLB
  misses instead of 5172, and take about as long.
- **The RISC-V specs**, where the Java CPU differed from them (commit 0c1d1cee3): registers of 32 bits (they kept
  Java longs, so that some comparisons and divisions went wrong), misaligned loads and stores as address misaligned
  exceptions, traps that go on with the handler, interrupts between instructions only and with their delegation,
  the privilege of mret, sret and sfence.vma, wfi, fence, jalr, the Reset pin, and the decoding of the system
  instructions. The interrupts were the visible one: the CPU took them between a store's address and data, and
  ran the store again after the handler, so LogOS's test programs printed doubled and lost characters
  ("Results: 0 passed" for 10). Now the sessions with 16 and 128 TLB entries are the same, and clean.

**The Verilog**, `riscvCpu` in `src/main/resources/resources/logisim/hdl/riscv`, generated for the CPU when its data
bus is separate (rv32imHdlGeneratorFactory: Verilog, at most 32 TLB entries, no instruction cache, no GDB server).
It does what the Java CPU does at each of the 405504 edges of LogOS's boot (the bootloader, the kernel in S-mode with
SV32, user programs, page table walks, interrupts, the Block Storage's DMA), at each edge of the boot and one of
LogOS's ten test programs, for each of them (31.2 million edges in all, stress_test's 11.2 million among them), and
at each of 196608 edges of random instruction words, interrupt requests and bus requests (illegal instructions and
their traps, every kind of instruction, random CSRs). On the GW2AR-18 it takes 7189 LUTs, 886 ALUs (carry chains),
1942 flip-flops, 6 block RAMs and a multiplier, and runs at up to 59 MHz. The check, in `tests/cpu`: the Java CPU
records its edges (with the Java system property `rv32im.trace`, for example
`JAVA_TOOL_OPTIONS=-Drv32im.trace=boot.trace logos_session.py jar dir`), and `tb_cpu_replay.v` plays the record to
riscvCpu and compares its outputs:

```bash
iverilog -DBINARY -DTRACE='"boot.trace"' -o /tmp/tb support/logos/tests/cpu/tb_cpu_replay.v \
  src/main/resources/resources/logisim/hdl/riscv/riscvCpu.v && vvp -n /tmp/tb | tail -1
```

How it works. The Java CPU, at a rising edge of its clock, takes the word on its data bus, does a step and sets up
its next bus cycle, all at once, and a RAM takes that bus cycle one FPGA clock after the tick. So riscvCpu does the
step before the tick: 8 FPGA clocks after a tick, when every device has answered (the SDRAM takes 7 at most), it
takes the data bus, the interrupt requests and Bus Request, and a sequencer does, over a few FPGA clocks, what the
Java CPU does at the next edge (a page table walk's step, an interrupt, the instruction, the next fetch and its
translation, a fault and the handler's fetch); at the tick the bus cycle it set up goes on the outputs. A division
takes 8 clocks (four bits of its quotient a clock) and its step about 15: so a CPU cycle needs 24 FPGA clocks,
which Logisim's ticks of 2048 kHz and slower give with the Tang Nano 20K's 27 MHz (26 FPGA clocks a cycle).

## 6. LogOS on the board

LogOS's computer.circ from its `fpga` branch (step 2's, with the CPU's TLB of 16 entries and the RAM in the SDRAM),
mapped to the Tang Nano 20K: the TTY on the board's serial TX and the Keyboard on its RX (the USB serial port, 115200
baud), the RAM on the SDRAM and the Block Storage on the TF slot. Logisim's FPGA flow builds it for ticks of 2048
kHz (`--test-fpga computer_mapped.circ main SIPEED_TANG_NANO_20K HDLONLY 2048000`, then the scripts it writes):
10956 LUTs and 1276 ALUs of the GW2AR-18's 20736, 2985 flip-flops and 8 block RAMs, up to 45 MHz.

The microSD card has LogOS's disk image from its block 0: `block_storage.bin` of LogOS's `make fs-image`, written
raw over whatever the card had, on a Mac for example as below (disk6 being the card in `diskutil list external`).
`board_session.py bitstream.fs [command ...]` then loads the bitstream into the FPGA, types the commands at LogOS's
shell over the serial port, each after a prompt, and prints the session.

```bash
diskutil unmountDisk /dev/disk6
sudo dd if=block_storage.bin of=/dev/rdisk6 bs=512
diskutil eject /dev/disk6
```

Checked on 4 November 2026: LogOS starts its shell 0.8 seconds after the bitstream is loaded (14 in Logisim), and
its ten test programs pass on the board, 81 tests of 81: the boot and the ten take 72 seconds (1143 in Logisim).
The session is Logisim's line for line but for one count that depends on when the interrupts come, which the
commands typed over the serial port change: redir_test's single read() of a pipe got 27 bytes of hello's output
instead of 26.
