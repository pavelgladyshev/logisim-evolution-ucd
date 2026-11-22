# Testing Logisim's GDB server

With Debug Enable set, Logisim's RV32IM CPU has a GDB server on its TCP Port, and gdb connects to it with
`target remote`. It shows gdb what OpenOCD shows it for the same CPU on an FPGA (`../debug/`), with the same
register numbers: x0-x31 by their ABI names (0-31), the pc (32), the CSRs the CPU implements (CSR n is 65 + n) and
`priv`, the privilege mode (4161: 0 U, 1 S, 3 M). `info registers` lists the integer registers and the pc,
`info registers csr` the CSRs and `priv`, and `p/x $mcause` one of them. gdb writes registers too (`set $mepc =
...`), CSRs as a CSR instruction writes them; `priv` it cannot write.

Memory (`x`, `print`, `set var`) is at the hart's addresses: in S- and U-mode with Sv32 on (satp), virtual ones,
which the server translates by walking the page table through the bus (megapages included, the permission bits
ignored, the CPU's TLB not touched); an unmapped address gives gdb's "Cannot access memory". In M-mode they are
physical. Each access is the one a program would make: a word (as `lw`/`sw`) where the address is a multiple of
4 and four bytes or more are left, a halfword (`lhu`/`sh`) where it is even, else a byte (`lbu`/`sb`). So `x/xw`
and `set {int}` at a device register (the Timer's, which decodes the whole address and takes whole words) read
and write it as `lw` and `sw` do, and `x/4xb` there shows what four `lbu` show: the low byte, then 0, 0, 0.
Breakpoints are at the pc's (virtual) address, in whichever process reaches it: all of logOS's programs start at
0x00400000, so a breakpoint there stops each of them.

Watchpoints (`watch`, `rwatch`, `awatch`) are the CPU's own: a store, a load or either to the range stops the CPU
before the access, as RISC-V's triggers do, and gdb, whose RISC-V watchpoints are "nonsteppable", steps the
instruction and then shows the old and new values, stopped after it. They cost nothing while none is set.

`monitor help` lists the server's commands. `monitor reset` resets the CPU as its Reset pin does (M-mode, the pc
at the reset address, mstatus.MIE and MPRV 0, mcause 0; memory and devices keep their state) and stops it there,
with gdb still connected. gdb does not know the registers changed: `maintenance flush register-cache` (or the next
`stepi`) shows them.

## The test

```sh
(cd ../../../.. && ./gradlew --offline shadowJar)   # the jar, in build/libs
make test                                          # or: make test LOGISIM_JAR=/its/path.jar
```

`make test` builds `prog.elf` (`start.S`, `prog.c`, `prog.ld`), and `run_test.py` puts it in the ROM of a copy of
`computer.circ` (`build/computer-test.circ`). `computer.circ` is the supplement's `simple-c` circuit (a ROM at
0, a RAM at 0x00100000, the Timer at its default addresses) with the CPU's Debug Enable set and its TCP Port
3340, and the Timer's clock that logOS's circuit has (simple-c's Timer has none, so it neither counts nor takes
writes); its own ROM still holds simple-c's program. The script starts Logisim on it without a window
(`--tty tty`, Java headless), so the CPU waits for gdb at its reset address, runs `gdb_test.gdb` in
`gdb-multiarch -batch`, stops Logisim, and compares what gdb printed (`build/gdb.log`) with `expected.txt`: one
regular expression per line, in order (see the script's header). It also checks that the server's messages did
not reach the simulated console (`build/logisim.log`). It prints PASS, or what did not match, and its exit
status is 0 or 1.

The session: the registers at the reset address (`info registers`, `info registers csr`: exactly the CSRs the
CPU implements, then `priv`), an unimplemented CSR read with `maint packet` (`xxxxxxxx`: unavailable), a
breakpoint in the trap handler after `do_ecall`'s `ecall` (`$mepc` is the ecall's address, `$mcause` 11, an
ecall from M-mode, `$mstatus` 0x1800, `$mscratch` the value `_start` put there), registers written by gdb (and
`set $priv` refused), a breakpoint in `square`, `continue`, `finish`, `stepi`, memory (`x/4xw`, `x/s`), the
Timer's mtimecmp (the value `main` stored, then `set {int}` and `x/xw`, `x/4xb`), `watch counter` twice (old and
new values), `rwatch counter`, `awatch squares[2]`, and `monitor help` and `monitor reset`; it ends with
`detach`. Then the sessions that follow it: packets sent without gdb (`qSupported`'s PacketSize, `vCont?`,
malformed and unknown packets, which get an error or the empty reply without ending the session, and Ctrl-C
while the CPU runs, which gets the stop reply T02), closed without a detach; a gdb that dies (SIGKILL) at a
breakpoint (`gdb_crash.gdb`); a gdb that finds the CPU ran on, the dead session's breakpoint gone, and ends with
`kill` (`gdb_after_crash.gdb`); and one more that attaches and detaches (`gdb_after_kill.gdb`).

A session ends with `detach`, `kill` or its connection closing (gdb quit, crashed, or its terminal closed). After
`kill` or a close, the CPU runs on as after a detach, without that session's breakpoints and watchpoints, and the
server waits for the next gdb.

It needs Java, gdb-multiarch, a RISC-V gcc (`CROSS=riscv64-unknown-elf-` by default) and Python 3. Only port 3340
is used; another one is a change to `computer.circ` and `gdb_test.gdb`.

## logOS

`make debug` in logOS starts it in text mode with the server on, and `make gdb` in another terminal connects
gdb with the kernel's symbols. In the kernel, `p $priv` is 1 and `p/x $satp`, `$sstatus`, `$sepc`, `$scause` and
`$stvec` are the supervisor's registers. For a user program, add its symbols (`add-symbol-file
build/hello-debug.elf`) and break in it (`break *0x00400b04`, hello's `main`): there `p $priv` is 0, and `x/4i $pc`
and `x/s` of its strings read its own pages. Checked on 21 November 2026 with Glendalough 3.0.3 (the kernel's
`syscall_dispatch`, the shell's `main`, hello's `main` and `puts`).
