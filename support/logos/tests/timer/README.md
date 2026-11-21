# The Timer: the Java component against its Verilog

The Timer of Logisim's riscv library is the RISC-V machine timer: `mtime` and `mtimecmp`, 64 bits each, on a data
bus of 32 bits (the privileged specification's Machine Timer Registers). In the simulator it is the Java component
(`Timer`, `TimerRegisters`); on an FPGA it is `riscvTimer.v`, in `src/main/resources/resources/logisim/hdl/riscv`,
which the FPGA flow appends to the module it generates for a Timer (`TimerHdlGeneratorFactory`), as it does with
`riscvCpu.v`. The two follow the same rules, and this test holds them to that, edge for edge.

## The rules

Address selects one of four words, the first of these that it equals (the sums modulo 2^32):

| Address | Word | With the default addresses |
| --- | --- | --- |
| the mtimecmp address | low word of mtimecmp | `0x02004000` |
| the mtime address | low word of mtime | `0x0200bff8` |
| the mtimecmp address + 4 | high word of mtimecmp | `0x02004004` |
| the mtime address + 4 | high word of mtime | `0x0200bffc` |

The two low words come first, so that the words the Timer had before it had 64 bits (up to 5.0.9) are where they
were whatever the two addresses are: with mtime in the word after mtimecmp, for example, mtime stays there and
mtimecmp's high word cannot be reached.

At a rising clock edge (on an FPGA: a tick), with the inputs as they are before it:

1. with Reset 1, mtime becomes 0 and mtimecmp all ones (`0xffffffffffffffff`), and nothing else happens: no
   write, no count;
2. otherwise, with Write 1, the selected word takes Data In, and the other three stay;
3. and then mtime, as the write left it, counts on by one, modulo 2^64. So a write of w to mtime's low word leaves
   w + 1 there after the edge, and a write of `0xffffffff` leaves 0 and carries into the high word.

Between edges, Reset 1 holds mtime at 0 and mtimecmp at all ones (in the Verilog from the next FPGA clock). The
interrupt request is 1 exactly while mtime >= mtimecmp, both unsigned and of 64 bits; in the Verilog it is a
register that every change of the two sets with them. Data Out is the selected word while Read is 1, and 0
otherwise. Both outputs follow the registers at once, with no clock. When the simulation starts, and when the FPGA
does, the registers are as after Reset.

What the specifications say of reset. mtime is zero after reset, as the ACLINT specification requires ("On MTIMER
device reset, the MTIME register is cleared to zero"). They leave mtimecmp undefined: the privileged specification
says nothing of either register's value after reset, and ACLINT says that "the MTIMECMP registers are in unknown
state". So correct software writes both words of mtimecmp before it enables the timer interrupt. All ones is this
Timer's own choice: no interrupt is requested before software sets mtimecmp, and software that writes only the low
word, as for the one word that the Timer had up to 5.0.9 (logOS up to Blackrock 2.1.3 and Glendalough 3.0.3 stores
`mtime + TIME_SLICE` there), leaves the high word at all ones and gets no interrupt. The value is
`TimerRegisters.MTIMECMP_RESET`; the generated module passes it to `riscvTimer`, whose own default is the same (a
unit test says so).

Where this differs from the Timer of 5.0.9, besides the 64 bits:

- The Timer of 5.0.9 started with mtimecmp 0, and had `0xffffffff` there only after Reset. In a circuit that leaves
  the Reset pin open, as LogOS's computer.circ does, its interrupt request was therefore 1 from the first clock edge
  until software set mtimecmp; now it is 0 until software has set both words.
- An edge with Reset 1 did the write and the count after the reset in the Java component, so that mtime was 1 until
  Reset was seen again, and not in the Verilog: with a Reset that ends just after a clock edge the two disagreed.
- The interrupt request changed only at clock edges, and in the Java component was not driven before the first one.
- In the Java component Data Out showed mtime as it was before the count, until an input changed (the clock's
  falling edge at the latest).
- An address attribute of more than 32 bits never matched in the Java component, and matched by its low 32 bits in
  the Verilog, as it does in both now.

## The test

```sh
./gradlew shadowJar       # in the repository's folder; then here:
make
```

It needs a JDK, Icarus Verilog (iverilog, vvp) and make. `TimerTrace.java` drives the Java component, as the
simulator does (its `propagate` after every change of its pins), through 300,000 steps a record, and writes each
step as a line: the inputs, whether a rising clock edge follows them, and then Data Out, the interrupt request and
both registers. `tb_timer_replay.v` gives `riscvTimer` the same inputs, with a tick where the record has an edge,
and compares the four after every step; at every FPGA clock it also checks that the interrupt request is the
comparison of the two registers. Each record is replayed three times, with 0, 1 and 7 FPGA clocks between the
inputs and the tick. The last line must be `all records agree`.

The steps are random, in runs that software makes or might: the count left alone, single reads and writes at the
four words and at addresses beside them, mtimecmp set a little ahead of mtime or behind it (with the
specification's three stores, with two in either order, with one), mtime set just below 2^32, 2^64, 2^31 and 2^63
and left to count across, Reset at an edge, between edges and held, and reads and writes between edges, which must
change nothing. There are five records, with different addresses: the default ones; high words past the top of the
address space and at `0x80000000`; mtime in the word after mtimecmp; mtimecmp in the word after mtime; and both at
one address. `build/<record>.txt` says what each record covers. On 21 November 2026 the five had 1,312,332 clock
edges in all, and the default one: 262,240 edges and 37,760 steps between edges, 436 edges with Reset and 804
resets between edges, 7,413 writes of the four words, 79,147 reads of them, 1,292 interrupt requests raised, 503
carries of the count into the high word, 184 wraps at 2^64, and 25,813 edges with mtime within 64 of 2^32.

`make STEPS=1000000 SEED=7` makes longer or other records. To see that the test can fail, change `riscvTimer.v`
(a copy: give it to `iverilog` in place of the real one). Twenty such changes were tried on 21 November 2026, and
each was found by every record that could meet it, within its first 28,000 steps: `>` for `>=`, no carry into the
high word, a signed comparison, a comparison of 63 bits, a count that stops at 2^64 - 1, an address plus 4 that
does not wrap, a write or a count at an edge with Reset, a Reset seen only at ticks, and so on.

With `-DGENERATED`, `tb_timer_replay.v` takes the module that Logisim's FPGA flow generates for a Timer (`Timer.v`
in the flow's `verilog/timer` folder, which has `riscvTimer` in it) instead of `riscvTimer.v`:

```sh
iverilog -DGENERATED -DTRACE='"build/default.trace"' -o build/tb tb_timer_replay.v .../verilog/timer/Timer.v
vvp -n build/tb
```

The other test of the Timer is `support/openxc7/tests/run_component_tests.sh riscv`: it puts Timers in a circuit
(`specs/riscv.txt`), and sends the same random inputs through Logisim's simulator, with its own propagation and
clock, and through the Verilog of the whole generated circuit. The Java rules themselves have unit tests,
`TimerRegistersTest` and `TimerTest` in `src/test/java/com/cburch/logisim/riscv/timer`.

## For software

On RV32 a register of 64 bits is two loads or two stores, and the timer counts on between them:

- **mtime** is read as its high word, its low word and its high word again, and taken only if the high word has
  not changed.
- **mtimecmp** is set with three stores, as the specification gives them: all ones to the low word, then the new
  high word, then the new low word. No value on the way is below both the old and the new one, so no interrupt is
  requested that neither asks for. Both words are to be written before the timer interrupt is enabled: after reset
  the specifications promise nothing of mtimecmp, and this Timer has all ones there, so that a store to the low
  word alone brings no interrupt.
