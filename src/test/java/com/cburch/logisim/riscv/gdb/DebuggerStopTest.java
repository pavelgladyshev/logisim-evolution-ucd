/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.gdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.GDBServer;
import com.cburch.logisim.riscv.cpu.csrs.PRIVILEGE_MODE;
import com.cburch.logisim.riscv.cpu.gdb.Watchpoint;
import com.cburch.logisim.riscv.cpu.rv32imData;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Where the CPU stops for gdb: with an instruction's fetch on the bus, where it takes the debugger's requests, also
 * when the next fetch needs a page table walk (Sv32, a TLB miss), and after exactly one instruction for a step.
 */
class DebuggerStopTest {

  private static final long ADDI_A0 = 0x00150513L;   // addi a0, a0, 1
  private static final long ADDI_A1 = 0x00158593L;   // addi a1, a1, 1

  private static final long SW_A0 = 0x00a5a223L;     // sw a0, 4(a1)
  private static final long LW_A2 = 0x0045a603L;     // lw a2, 4(a1)

  /** Takes the stop replies that the CPU would hand the server's thread. */
  private static final class Replies extends GDBServer {
    String last;

    @Override
    public void setDebuggerResponse(String response) {
      last = response;
    }
  }

  private final Map<Long, Long> memory = new HashMap<>();
  private final Replies replies = new Replies();
  private rv32imData cpu;

  /** One clock cycle: the memory answers the address the CPU put on the bus in the cycle before (as a ROM does). */
  private void cycle() {
    final Value address = cpu.getAddress();
    long data = 0;
    if (address.isFullyDefined()) {
      data = memory.getOrDefault(address.toLongValue() & ~3L, 0L);
      if (cpu.getMemWrite() == Value.TRUE) memory.put(address.toLongValue() & ~3L, cpu.getOutputData().toLongValue());
    }
    cpu.update(data, 0, 0, 0);
  }

  private void runUntilStopped() {
    for (int i = 0; i < 200 && cpu.getCpuState() != rv32imData.CPUState.STOPPED; i++) cycle();
    assertEquals(rv32imData.CPUState.STOPPED, cpu.getCpuState());
  }

  private void assertTakesDebuggerRequests() {
    final boolean[] done = {false};
    cpu.setDebuggerRequest(dataIn -> {
      done[0] = true;
      return true;
    });
    for (int i = 0; i < 5 && !done[0]; i++) cycle();
    assertTrue(done[0], "the stopped CPU takes the debugger's requests");
  }

  @BeforeEach
  void setup() {
    cpu = new rv32imData(Value.FALSE, 0, 1234, false, false, rv32imData.CPUState.RUNNING, null);
    cpu.setGDBServer(replies);
    // Sv32: root table at 0x1000, its entry for 0x00400000 points to the table at 0x2000, which maps the pages
    // 0x00400000 and 0x00401000 to 0x10000 and 0x11000 (readable, executable, accessed)
    memory.put(0x1000L + 1 * 4, (2L << 10) | 0x1);
    memory.put(0x2000L + 0 * 4, (0x10L << 10) | 0x4b);
    memory.put(0x2000L + 1 * 4, (0x11L << 10) | 0x4b);
    memory.put(0x10ffcL, ADDI_A0);   // the last instruction of the first page
    memory.put(0x11000L, ADDI_A1);   // the first two of the second
    memory.put(0x11004L, ADDI_A1);
    cpu.setCSR(0x180, 0x80000001L);
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    cpu.getPC().set(0x400ffcL);
    cpu.fetchNextInstruction();
  }

  @Test
  void stepIntoAPageThatNeedsAWalk() {
    cpu.setCpuState(rv32imData.CPUState.SINGLE_STEP);
    runUntilStopped();
    assertEquals(1, cpu.getX(10), "the step's instruction ran");
    assertEquals(0, cpu.getX(11), "and not the next one");
    assertEquals(0x401000L, cpu.getPC().get());
    assertTakesDebuggerRequests();
  }

  @Test
  void breakpointOnAPageThatNeedsAWalk() {
    cpu.setBreakpoint(0x401000L);
    runUntilStopped();
    assertEquals(1, cpu.getX(10));
    assertEquals(0, cpu.getX(11), "stopped before the instruction at the breakpoint");
    assertEquals(0x401000L, cpu.getPC().get());
    assertTakesDebuggerRequests();
  }

  @Test
  void stepWithinAPage() {
    cpu.setCpuState(rv32imData.CPUState.SINGLE_STEP);
    runUntilStopped();
    cpu.setCpuState(rv32imData.CPUState.SINGLE_STEP);
    cpu.fetchNextInstruction();
    runUntilStopped();
    assertEquals(1, cpu.getX(11));
    assertEquals(0x401004L, cpu.getPC().get());
    assertTakesDebuggerRequests();
  }

  /** A store at 0x400ffc to 0x104 (M-mode: physical addresses), a load from it, then the instruction after. */
  private void storeAndLoad() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
    cpu.getPtw().reset();   // the walk setup's fetch started in S-mode
    memory.put(0x400ffcL, SW_A0);
    memory.put(0x401000L, LW_A2);
    memory.put(0x401004L, ADDI_A1);
    cpu.setX(10, 77);
    cpu.setX(11, 0x100);
    cpu.getPC().set(0x400ffcL);
    cpu.fetchNextInstruction();
  }

  @Test
  void writeWatchpointStopsBeforeTheStore() {
    storeAndLoad();
    cpu.setWatchpoint(new Watchpoint(Watchpoint.Kind.WRITE, 0x104, 4));
    runUntilStopped();
    assertEquals("T05watch:104;", replies.last);
    assertEquals(0x400ffcL, cpu.getPC().get(), "before the store, as RISC-V's triggers stop");
    assertEquals(null, memory.get(0x104L), "the store has not been made");
    assertTakesDebuggerRequests();
    // gdb steps it with the watchpoint removed
    cpu.removeWatchpoint(new Watchpoint(Watchpoint.Kind.WRITE, 0x104, 4));
    cpu.setCpuState(rv32imData.CPUState.SINGLE_STEP);
    cpu.fetchNextInstruction();
    runUntilStopped();
    assertEquals("T05", replies.last);
    assertEquals(77L, memory.get(0x104L));
    assertEquals(0x401000L, cpu.getPC().get());
  }

  @Test
  void readWatchpointIgnoresTheStoreAndStopsBeforeTheLoad() {
    storeAndLoad();
    cpu.setWatchpoint(new Watchpoint(Watchpoint.Kind.READ, 0x106, 1));
    runUntilStopped();
    assertEquals("T05rwatch:106;", replies.last);
    assertEquals(0x401000L, cpu.getPC().get());
    assertEquals(0, cpu.getX(12));
  }

  @Test
  void noWatchpointNoStop() {
    storeAndLoad();
    cpu.setWatchpoint(new Watchpoint(Watchpoint.Kind.ACCESS, 0x200, 4));
    cpu.setBreakpoint(0x401004L);
    runUntilStopped();
    assertEquals("T05", replies.last);
    assertEquals(0x401004L, cpu.getPC().get());
    assertEquals(77, cpu.getX(12));
  }

  @Test
  void resetForDebugger() {
    cpu.setCpuState(rv32imData.CPUState.SINGLE_STEP);
    runUntilStopped();
    cpu.resetForDebugger();
    assertEquals(rv32imData.CPUState.STOPPED, cpu.getCpuState());
    assertEquals(0L, cpu.getPC().get(), "the reset address");
    assertEquals(PRIVILEGE_MODE.MACHINE, cpu.getCurrentPrivilegeMode());
    assertEquals(0L, cpu.getCSRValue(0x342), "mcause");
    assertEquals(0L, cpu.getAddress().toLongValue(), "the fetch at the reset address is on the bus");
    assertTakesDebuggerRequests();
  }
}
