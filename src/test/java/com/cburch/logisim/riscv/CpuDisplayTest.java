/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.CpuDrawSupport;
import com.cburch.logisim.riscv.cpu.RiscvDisassembler;
import com.cburch.logisim.riscv.cpu.csrs.MMCSR;
import com.cburch.logisim.riscv.cpu.rv32im;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.Test;

/** The CPU's display: the disassembler, the instruction at the pc, the highlight, and the CSR panel's attribute. */
public class CpuDisplayTest {

  /** Words, their address, and what gdb-multiarch 15.1 (x/i, set architecture riscv:rv32) shows for them. */
  private static final Object[][] GDB = {
    {0xf0480283L, 0x80000000L, "lb t0,-252(a6)"},
    {0x47e59c83L, 0x80000004L, "lh s9,1150(a1)"},
    {0x9bf12503L, 0x80000008L, "lw a0,-1601(sp)"},
    {0x2e42c583L, 0x80000010L, "lbu a1,740(t0)"},
    {0x59b2d283L, 0x80000014L, "lhu t0,1435(t0)"},
    {0xe69183a3L, 0x80000020L, "sb s1,-409(gp)"},
    {0x68be9123L, 0x80000024L, "sh a1,1666(t4)"},
    {0x78012523L, 0x80000028L, "sw zero,1930(sp)"},
    {0x06730313L, 0x80000040L, "addi t1,t1,103"},
    {0x01329213L, 0x80000044L, "slli tp,t0,0x13"},
    {0xe345a613L, 0x80000050L, "slti a2,a1,-460"},
    {0x6f2eb913L, 0x80000054L, "sltiu s2,t4,1778"},
    {0xa93e4713L, 0x80000058L, "xori a4,t3,-1389"},
    {0x00885013L, 0x8000005cL, "srli zero,a6,0x8"},
    {0x4079db13L, 0x80000060L, "srai s6,s3,0x7"},
    {0x77cb6813L, 0x80000068L, "ori a6,s6,1916"},
    {0x78f67793L, 0x8000006cL, "andi a5,a2,1935"},
    {0x003d8ab3L, 0x80000070L, "add s5,s11,gp"},
    {0x40f90bb3L, 0x80000074L, "sub s7,s2,a5"},
    {0x02bc8c33L, 0x80000078L, "mul s8,s9,a1"},
    {0x00e51fb3L, 0x80000080L, "sll t6,a0,a4"},
    {0x02329f33L, 0x80000088L, "mulh t5,t0,gp"},
    {0x00092633L, 0x80000090L, "sltz a2,s2"},
    {0x0374a533L, 0x80000098L, "mulhsu a0,s1,s7"},
    {0x01c8b233L, 0x800000a0L, "sltu tp,a7,t3"},
    {0x032b38b3L, 0x800000a8L, "mulhu a7,s6,s2"},
    {0x0158c833L, 0x800000b0L, "xor a6,a7,s5"},
    {0x02f1cd33L, 0x800000b8L, "div s10,gp,a5"},
    {0x0093dbb3L, 0x800000c0L, "srl s7,t2,s1"},
    {0x41245a33L, 0x800000c4L, "sra s4,s0,s2"},
    {0x020e50b3L, 0x800000c8L, "divu ra,t3,zero"},
    {0x0149eeb3L, 0x800000d0L, "or t4,s3,s4"},
    {0x03fce733L, 0x800000d8L, "rem a4,s9,t6"},
    {0x0037fbb3L, 0x800000e0L, "and s7,a5,gp"},
    {0x036cfab3L, 0x800000e8L, "remu s5,s9,s6"},
    {0xff7480e3L, 0x800000f0L, "beq s1,s7,0x800000d0"},
    {0xa6879163L, 0x800000f4L, "bne a5,s0,0x7ffff356"},
    {0xb260c9e3L, 0x80000100L, "blt ra,t1,0x7ffffc32"},
    {0x8660d0e3L, 0x80000104L, "bge ra,t1,0x7ffff964"},
    {0x32cce3e3L, 0x80000108L, "bltu s9,a2,0x80000c2e"},
    {0x365a7e63L, 0x8000010cL, "bgeu s4,t0,0x80000488"},
    {0x81e8ea37L, 0x80000110L, "lui s4,0x81e8e"},
    {0xcb801417L, 0x80000114L, "auipc s0,0xcb801"},
    {0x8b36cd6fL, 0x80000118L, "jal s10,0x7ff6c9ca"},
    {0xafc284e7L, 0x8000011cL, "jalr s1,-1284(t0)"},
    {0xef32dc83L, 0x80000160L, "lhu s9,-269(t0)"},
    {0x52d40323L, 0x8000016cL, "sb a3,1318(s0)"},
    {0x980c1623L, 0x80000170L, "sh zero,-1652(s8)"},
    {0xda75a0a3L, 0x80000174L, "sw t2,-607(a1)"},
    {0x4184a093L, 0x8000019cL, "slti ra,s1,1048"},
    {0x9b913893L, 0x800001a0L, "sltiu a7,sp,-1607"},
    {0x03c007b3L, 0x800001c4L, "mul a5,zero,t3"},
    {0x00c4ac33L, 0x800001dcL, "slt s8,s1,a2"},
    {0x03155033L, 0x80000214L, "divu zero,a0,a7"},
    {0x000a68b3L, 0x8000021cL, "or a7,s4,zero"},
    {0x70168867L, 0x8000026cL, "jalr a6,1793(a3)"},
    {0x72eb0283L, 0x80000298L, "lb t0,1838(s6)"},
    {0x8a499183L, 0x8000029cL, "lh gp,-1884(s3)"},
    {0x16ac2503L, 0x800002a0L, "lw a0,362(s8)"},
    {0x8f404113L, 0x800002f0L, "xori sp,zero,-1804"},
    {0x937d6793L, 0x80000300L, "ori a5,s10,-1737"},
    {0x9e627993L, 0x80000304L, "andi s3,tp,-1562"},
    {0x01502ab3L, 0x80000328L, "sgtz s5,s5"},
    {0x260cc1e3L, 0x80000398L, "bltz s9,0x80000dfa"},
    {0x6365cf13L, 0x8000043cL, "xori t5,a1,1590"},
    {0x480557e3L, 0x800004e8L, "bgez a0,0x80001176"},
    {0xd77a4d83L, 0x80000540L, "lbu s11,-649(s4)"},
    {0x024047b3L, 0x800005e8L, "div a5,zero,tp"},
    {0x024069b3L, 0x80000608L, "rem s3,zero,tp"},
    {0x9caf8993L, 0x800006bcL, "addi s3,t6,-1590"},
    {0x01a04fb3L, 0x80000878L, "xor t6,zero,s10"},
    {0x0a706813L, 0x8000097cL, "ori a6,zero,167"},
    {0x03703d33L, 0x80000b08L, "mulhu s10,zero,s7"},
    {0x00305733L, 0x80000b20L, "srl a4,zero,gp"},
    {0x41f00db3L, 0x80000eb8L, "neg s11,t6"},
    {0x000134b3L, 0x80000ee4L, "sltu s1,sp,zero"},
    {0xa4067063L, 0x8000109cL, "bgeu a2,zero,0x800002dc"},
    {0x02012f33L, 0x80001174L, "mulhsu t5,sp,zero"},
    {0x00000013L, 0x80001374L, "nop"},
    {0x00004013L, 0x80001378L, "xori zero,zero,0"},
    {0x00003013L, 0x8000137cL, "sltiu zero,zero,0"},
    {0x00002013L, 0x80001380L, "slti zero,zero,0"},
    {0x00000067L, 0x80001384L, "jr zero"},
    {0x00100013L, 0x8000138cL, "li zero,1"},
    {0x00103013L, 0x80001394L, "seqz zero,zero"},
    {0x00100067L, 0x8000139cL, "jr 1(zero)"},
    {0xfff00013L, 0x800013a4L, "li zero,-1"},
    {0xfff04013L, 0x800013a8L, "not zero,zero"},
    {0xfff03013L, 0x800013acL, "sltiu zero,zero,-1"},
    {0xfff02013L, 0x800013b0L, "slti zero,zero,-1"},
    {0xfff00067L, 0x800013b4L, "jr -1(zero)"},
    {0x40000033L, 0x80001404L, "neg zero,zero"},
    {0x00003033L, 0x80001408L, "snez zero,zero"},
    {0x00002033L, 0x8000140cL, "sltz zero,zero"},
    {0x00000033L, 0x80001410L, "add zero,zero,zero"},
    {0x80000863L, 0x80001414L, "beqz zero,0x80000424"},
    {0x80001863L, 0x8000141cL, "bnez zero,0x8000042c"},
    {0x80005863L, 0x8000143cL, "blez zero,0x8000044c"},
    {0x80006863L, 0x80001444L, "bltu zero,zero,0x80000454"},
    {0x00503033L, 0x80001458L, "snez zero,t0"},
    {0x80504863L, 0x80001484L, "bgtz t0,0x80000494"},
    {0x00008013L, 0x800014a4L, "mv zero,ra"},
    {0x00008067L, 0x800014b4L, "ret"},
    {0x0010b013L, 0x800014c4L, "seqz zero,ra"},
    {0xfff0c013L, 0x800014d8L, "not zero,ra"},
    {0x40008033L, 0x80001534L, "sub zero,ra,zero"},
    {0x2000006fL, 0x80001834L, "j 0x80001a34"},
    {0x000000e7L, 0x8000184cL, "jalr zero"},
    {0x00000567L, 0x80001d14L, "jalr a0,zero"},
    {0x00000073L, 0x800021ccL, "ecall"},
    {0x00100073L, 0x800021d0L, "ebreak"},
    {0x30200073L, 0x800021d4L, "mret"},
    {0x10200073L, 0x800021d8L, "sret"},
    {0x10500073L, 0x800021dcL, "wfi"},
    {0x12000073L, 0x800021e0L, "sfence.vma"},
    {0x0ff0000fL, 0x80002214L, "fence"},
    {0x8330000fL, 0x80002218L, "fence.tso"},
    {0x30001073L, 0x8000222cL, "csrw mstatus,zero"},
    {0x30059073L, 0x80002230L, "csrw mstatus,a1"},
    {0x30001573L, 0x80002234L, "csrrw a0,mstatus,zero"},
    {0x30059573L, 0x80002238L, "csrrw a0,mstatus,a1"},
    {0x30002073L, 0x8000223cL, "csrr zero,mstatus"},
    {0x3005a073L, 0x80002240L, "csrs mstatus,a1"},
    {0x3005a573L, 0x80002248L, "csrrs a0,mstatus,a1"},
    {0x30003073L, 0x8000224cL, "csrc mstatus,zero"},
    {0x3005b073L, 0x80002250L, "csrc mstatus,a1"},
    {0x30003573L, 0x80002254L, "csrrc a0,mstatus,zero"},
    {0x3005b573L, 0x80002258L, "csrrc a0,mstatus,a1"},
    {0x30005073L, 0x8000225cL, "csrwi mstatus,0"},
    {0x30005573L, 0x80002264L, "csrrwi a0,mstatus,0"},
    {0x30006073L, 0x8000226cL, "csrsi mstatus,0"},
    {0x30006573L, 0x80002274L, "csrrsi a0,mstatus,0"},
    {0x30007073L, 0x8000227cL, "csrci mstatus,0"},
    {0x30007573L, 0x80002284L, "csrrci a0,mstatus,0"},
    {0xc0001073L, 0x80002b2cL, "unimp"},
  };

  @Test
  void disassemblesAsGdbDoes() {
    for (final var v : GDB) {
      assertEquals(v[2], RiscvDisassembler.disassemble((Long) v[0], (Long) v[1]), String.format("%08x", v[0]));
    }
  }

  @Test
  void disassemblesTheRestAsWords() {
    assertEquals(".word 0x00000000", RiscvDisassembler.disassemble(0, 0));
    assertEquals(".word 0xffffffff", RiscvDisassembler.disassemble(0xffffffffL, 0));
    assertEquals(".word 0x00200073", RiscvDisassembler.disassemble(0x00200073L, 0));   // uret: not RV32IM
    assertEquals(".word 0x40899b13", RiscvDisassembler.disassemble(0x40899b13L, 0));   // slli with funct7 0x20
    assertEquals(".word 0x12000033", RiscvDisassembler.disassemble(0x12000033L, 0));   // funct7 0x09: no such R-type
    assertEquals("fence.i", RiscvDisassembler.disassemble(0x0000100fL, 0));
    assertEquals("unimp", RiscvDisassembler.disassemble(0xc0001073L, 0));
    // a CSR that the CPU does not implement, by its number
    assertEquals("csrr a0,0xc00", RiscvDisassembler.disassemble(0xc0002573L, 0));
    assertEquals("csrw satp,t0", RiscvDisassembler.disassemble(0x18029073L, 0));
    assertEquals("addi sp,sp,-16", RiscvDisassembler.disassemble(0xff010113L, 0));
  }

  private static rv32imData cpu() {
    return new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
  }

  @Test
  void highlightsWhatTheLastInstructionWrote() {
    final var cpu = cpu();
    for (int i = 0; i < 32; i++) assertFalse(cpu.wasRegisterWritten(i));
    cpu.update(0x00c00293L, 0, 0, 0);   // li t0,12
    assertTrue(cpu.wasRegisterWritten(5));
    cpu.update(0x00128313L, 0, 0, 0);   // addi t1,t0,1
    assertFalse(cpu.wasRegisterWritten(5));
    assertTrue(cpu.wasRegisterWritten(6));
    cpu.update(0x00100013L, 0, 0, 0);   // addi zero,zero,1: x0 never
    for (int i = 0; i < 32; i++) assertFalse(cpu.wasRegisterWritten(i));
    // a load: nothing while it waits for its data, its rd after
    cpu.update(0x0002a383L, 0, 0, 0);   // lw t2,0(t0)
    for (int i = 0; i < 32; i++) assertFalse(cpu.wasRegisterWritten(i));
    cpu.update(1234, 0, 0, 0);
    assertTrue(cpu.wasRegisterWritten(7));
  }

  @Test
  void highlightsTheCsrsTheLastInstructionChanged() {
    final var cpu = cpu();
    final int mscratch = MMCSR.MSCRATCH.getAddress();
    final int mepc = MMCSR.MEPC.getAddress();
    cpu.update(0x00c00293L, 0, 0, 0);   // li t0,12
    cpu.update(0x34029073L, 0, 0, 0);   // csrw mscratch,t0
    assertTrue(cpu.wasCsrChanged(mscratch));
    assertFalse(cpu.wasModeChanged());
    cpu.update(0x34029073L, 0, 0, 0);   // the same value again: written all the same
    assertTrue(cpu.wasCsrChanged(mscratch));
    cpu.update(0x34002573L, 0, 0, 0);   // csrr a0,mscratch: read, not written
    assertFalse(cpu.wasCsrChanged(mscratch));
    assertTrue(cpu.wasRegisterWritten(10));
    cpu.update(0x00000073L, 0, 0, 0);   // ecall: mepc and mcause
    assertTrue(cpu.wasCsrChanged(mepc));
    assertTrue(cpu.wasCsrChanged(MMCSR.MCAUSE.getAddress()));
    assertFalse(cpu.wasCsrChanged(mscratch));
  }

  @Test
  void showsTheInstructionAtThePc() {
    final var cpu = cpu();
    // fetching: the word that memory answers at the pc
    assertEquals(0x00c00293L, cpu.instructionWordAtPc(Value.createKnown(32, 0x00c00293L)));
    // with Data In undefined, the word found last at the same pc
    assertEquals(0x00c00293L, cpu.instructionWordAtPc(Value.createUnknown(com.cburch.logisim.data.BitWidth.create(32))));
    cpu.update(0x00c00293L, 0, 0, 0);
    assertEquals(-1, cpu.instructionWordAtPc(Value.createUnknown(com.cburch.logisim.data.BitWidth.create(32))));
    // a load waits for its data: the pc has not moved, and the word is the one being executed
    cpu.update(0x0002a383L, 0, 0, 0);
    assertEquals(0x400004L, cpu.getPC().get());
    assertEquals(0x0002a383L, cpu.instructionWordAtPc(Value.createKnown(32, 0x5555L)));
  }

  @Test
  void padsHexRegistersToEightDigits() {
    assertEquals("0x001f0010", CpuDrawSupport.registerText(0x1f0010, true));
    assertEquals("0x00000000", CpuDrawSupport.registerText(0, true));
    assertEquals("0xffffffff", CpuDrawSupport.registerText(-1, true));
    assertEquals("-1", CpuDrawSupport.registerText(-1, false));
    assertEquals("2031632", CpuDrawSupport.registerText(0x1f0010, false));
  }

  @Test
  void spellsTheCauseOutOnlyAfterATrap() {
    final var cpu = cpu();
    assertEquals("no trap yet", CpuDrawSupport.causeLine(cpu, false));
    assertEquals("no trap yet", CpuDrawSupport.causeLine(cpu, true));
    cpu.update(0x00000073L, 0, 0, 0);   // ecall in M-mode: a trap into M-mode
    assertEquals("exception 11: ecall from M-mode", CpuDrawSupport.causeLine(cpu, false));
    assertEquals("no trap yet", CpuDrawSupport.causeLine(cpu, true));
    // the reset forgets it, as mcause is 0 again
    cpu.reset(0x400000, 1234, false);
    assertEquals("no trap yet", CpuDrawSupport.causeLine(cpu, false));
  }

  @Test
  void spellsTheSupervisorCauseOutAfterADelegatedTrap() {
    final var cpu = cpu();
    cpu.setCSR(MMCSR.MEDELEG.getAddress(), 1 << 8);   // delegate ecall from U-mode
    cpu.setCurrentPrivilegeMode(com.cburch.logisim.riscv.cpu.csrs.PRIVILEGE_MODE.USER);
    cpu.update(0x00000073L, 0, 0, 0);   // ecall in U-mode: a trap into S-mode
    assertEquals("exception 8: ecall from U-mode", CpuDrawSupport.causeLine(cpu, true));
    assertEquals("no trap yet", CpuDrawSupport.causeLine(cpu, false));
  }

  @Test
  void csrPanelAttribute() {
    final var factory = new rv32im();
    final var attrs = factory.createAttributeSet();
    assertSame(rv32im.CSR_PANEL_NONE, attrs.getValue(rv32im.ATTR_CSR_PANEL));
    // none: the bounds of 5.0.9
    assertEquals(Bounds.create(-60, -20, 180, 757), factory.getOffsetBounds(attrs));
    assertSame(rv32im.CSR_PANEL_SUPERVISOR, rv32im.ATTR_CSR_PANEL.parse("supervisor"));
    assertEquals("machine", rv32im.ATTR_CSR_PANEL.toStandardString(rv32im.CSR_PANEL_MACHINE));
    attrs.setValue(rv32im.ATTR_CSR_PANEL, rv32im.CSR_PANEL_MACHINE);
    final var machine = factory.getOffsetBounds(attrs);
    attrs.setValue(rv32im.ATTR_CSR_PANEL, rv32im.CSR_PANEL_SUPERVISOR);
    final var supervisor = factory.getOffsetBounds(attrs);
    assertEquals(-20, machine.getY());
    assertEquals(180, supervisor.getWidth());
    assertTrue(machine.getHeight() > 757 && supervisor.getHeight() > machine.getHeight());
  }
}
