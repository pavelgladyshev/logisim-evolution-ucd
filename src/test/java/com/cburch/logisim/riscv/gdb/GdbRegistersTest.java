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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.csrs.PRIVILEGE_MODE;
import com.cburch.logisim.riscv.cpu.gdb.GdbRegisters;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The GDB server's registers: OpenOCD's numbers (x 0-31, pc 32, CSR n 65 + n, priv 4161), and their replies. */
class GdbRegistersTest {

  private rv32imData cpu;

  @BeforeEach
  void setup() {
    cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
  }

  @Test
  void numbering() {
    assertEquals(32, GdbRegisters.PC);
    assertEquals(65, GdbRegisters.FIRST_CSR);
    assertEquals(65 + 0xfff, GdbRegisters.LAST_CSR);
    assertEquals(4161, GdbRegisters.PRIV);
    assertTrue(GdbRegisters.exists(0));
    assertTrue(GdbRegisters.exists(32));
    assertFalse(GdbRegisters.exists(33));   // the FPU's registers: there is no FPU
    assertFalse(GdbRegisters.exists(64));
    assertTrue(GdbRegisters.exists(65));
    assertTrue(GdbRegisters.exists(4160));
    assertTrue(GdbRegisters.exists(4161));
    assertFalse(GdbRegisters.exists(4162));
    assertFalse(GdbRegisters.exists(-1));
  }

  @Test
  void integerRegistersAndPc() {
    cpu.setX(1, 0x12345678);
    cpu.setX(10, -2);
    assertEquals("78563412", GdbRegisters.read(cpu, 1));
    assertEquals("feffffff", GdbRegisters.read(cpu, 10));
    assertEquals("00000000", GdbRegisters.read(cpu, 0));
    assertEquals("00004000", GdbRegisters.read(cpu, 32));   // the reset address, 0x400000
  }

  @Test
  void implementedCsrsByTheirNumbers() {
    cpu.setCSR(0x341, 0x80001234L);   // mepc
    cpu.setCSR(0x340, 0xcafe);        // mscratch
    assertEquals("34120080", GdbRegisters.read(cpu, 65 + 0x341));
    assertEquals("feca0000", GdbRegisters.read(cpu, 65 + 0x340));
  }

  @Test
  void csrListIsWhatTheModelImplements() {
    assertEquals(19, GdbRegisters.CSRS.size());
    for (String name : new String[] {"sstatus", "sie", "stvec", "sscratch", "sepc", "scause", "stval", "sip", "satp",
        "mstatus", "medeleg", "mideleg", "mie", "mtvec", "mscratch", "mepc", "mcause", "mtval", "mip"}) {
      assertTrue(GdbRegisters.CSRS.containsKey(name), name);
    }
    assertEquals(0x341, GdbRegisters.CSRS.get("mepc"));
    assertEquals(0x180, GdbRegisters.CSRS.get("satp"));
    int previous = -1;
    for (int address : GdbRegisters.CSRS.values()) {
      assertTrue(address > previous, "in address order");
      previous = address;
    }
  }

  @Test
  void unimplementedCsrIsUnavailable() {
    assertEquals("xxxxxxxx", GdbRegisters.read(cpu, 65 + 0xf14));   // mhartid
    assertEquals("xxxxxxxx", GdbRegisters.read(cpu, 65 + 0xc00));   // cycle
    assertEquals("xxxxxxxx", GdbRegisters.read(cpu, 65));           // ustatus
    assertEquals("E01", GdbRegisters.write(cpu, 65 + 0x7c0, 1));
  }

  @Test
  void unknownNumberIsAnError() {
    assertEquals("E00", GdbRegisters.read(cpu, 33));
    assertEquals("E00", GdbRegisters.read(cpu, 5000));
    assertEquals("E01", GdbRegisters.write(cpu, 40, 1));
  }

  @Test
  void priv() {
    assertEquals("03000000", GdbRegisters.read(cpu, 4161));
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    assertEquals("01000000", GdbRegisters.read(cpu, 4161));
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.USER);
    assertEquals("00000000", GdbRegisters.read(cpu, 4161));
  }

  @Test
  void privCannotBeWritten() {
    assertEquals("E01", GdbRegisters.write(cpu, 4161, 1));
    assertEquals(PRIVILEGE_MODE.MACHINE, cpu.getCurrentPrivilegeMode());
  }

  @Test
  void writes() {
    assertEquals("OK", GdbRegisters.write(cpu, 5, GdbRegisters.parseValue("78563412")));
    assertEquals(0x12345678, cpu.getX(5));
    assertEquals("OK", GdbRegisters.write(cpu, 0, 7));
    assertEquals(0, cpu.getX(0));
    assertEquals("OK", GdbRegisters.write(cpu, 32, 0x100));
    assertEquals(0x100, cpu.getPC().get());
    assertEquals("OK", GdbRegisters.write(cpu, 65 + 0x305, 0x2000));   // mtvec
    assertEquals(0x2000, cpu.getCSRValue(0x305));
  }

  @Test
  void csrWritesTakeTheModelsPath() {
    // sstatus is a view of mstatus: writing it changes only mstatus's S-mode fields (SIE is bit 1, MIE bit 3)
    assertEquals("OK", GdbRegisters.write(cpu, 65 + 0x100, 0xffffffffL));
    assertEquals(1, (cpu.getCSRValue(0x300) >> 1) & 1);
    assertEquals(0, (cpu.getCSRValue(0x300) >> 3) & 1);
  }

  @Test
  void valueParsing() {
    assertEquals(0x12345678L, GdbRegisters.parseValue("78563412"));
    assertEquals(0xffffffffL, GdbRegisters.parseValue("ffffffff"));
    assertEquals(3L, GdbRegisters.parseValue("03"));
    assertEquals("efbeadde", GdbRegisters.formatWord(0xdeadbeefL));
  }
}
