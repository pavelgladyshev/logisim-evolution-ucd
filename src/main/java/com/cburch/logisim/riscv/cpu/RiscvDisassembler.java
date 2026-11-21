/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.csrs.MMCSR;
import com.cburch.logisim.riscv.cpu.csrs.SCSR;

import java.util.HashMap;
import java.util.Map;

/**
 * A disassembler of one RV32IM instruction word, with Zicsr, FENCE, FENCE.I, ECALL, EBREAK, MRET, SRET, WFI and
 * SFENCE.VMA, for the CPU's display. It writes what gdb's {@code x/i} writes (gdb and objdump share GNU binutils'
 * libopcodes): ABI register names, the same pseudo-instructions ({@code li}, {@code mv}, {@code ret}, {@code csrr},
 * {@code beqz}...), decimal immediates, {@code lui}, {@code auipc} and shift amounts in hex, and branch and jump
 * targets as absolute addresses; one space, not a tab, after the mnemonic, and none of gdb's {@code <symbol>} or
 * {@code # comment} additions. A word that is none of these is {@code .word 0x...}. CSRs are named when the CPU
 * implements them (ControlAndStatusRegisters), and by number otherwise.
 */
public final class RiscvDisassembler {

  private RiscvDisassembler() {}

  private static final String[] REG = IntegerRegisters.registerABINames;

  /** The CSRs that the CPU implements, by name. */
  private static final Map<Integer, String> CSR_NAMES = new HashMap<>();

  static {
    for (final var csr : new MMCSR[] {MMCSR.MVENDORID, MMCSR.MARCHID, MMCSR.MIMPID, MMCSR.MHARTID, MMCSR.MSTATUS,
        MMCSR.MISA, MMCSR.MEDELEG, MMCSR.MIDELEG, MMCSR.MIE, MMCSR.MTVEC, MMCSR.MSCRATCH, MMCSR.MEPC, MMCSR.MCAUSE,
        MMCSR.MTVAL, MMCSR.MIP}) {
      CSR_NAMES.put(csr.getAddress(), csr.name().toLowerCase());
    }
    for (final var csr : new SCSR[] {SCSR.SSTATUS, SCSR.SIE, SCSR.STVEC, SCSR.SSCRATCH, SCSR.SEPC, SCSR.SCAUSE,
        SCSR.STVAL, SCSR.SIP, SCSR.SATP}) {
      CSR_NAMES.put(csr.getAddress(), csr.name().toLowerCase());
    }
  }

  /** The CSR's name, or its number in hex. */
  public static String csrName(int csr) {
    final var name = CSR_NAMES.get(csr);
    return name != null ? name : hex(csr);
  }

  private static String hex(long value) {
    return "0x" + Long.toHexString(value & 0xFFFFFFFFL);
  }

  /** The instruction word at address pc, disassembled. */
  public static String disassemble(long word, long pc) {
    final var text = decode((int) word, pc & 0xFFFFFFFFL);
    return text != null ? text : String.format(".word 0x%08x", word & 0xFFFFFFFFL);
  }

  private static String decode(int w, long pc) {
    final int opcode = w & 0x7F;
    final int rd = (w >>> 7) & 0x1F;
    final int f3 = (w >>> 12) & 0x7;
    final int rs1 = (w >>> 15) & 0x1F;
    final int rs2 = (w >>> 20) & 0x1F;
    final int f7 = w >>> 25;
    final int immI = w >> 20;
    final int immS = ((w >> 25) << 5) | rd;
    final int immB = ((w >> 31) << 12) | (((w >>> 7) & 1) << 11) | (((w >>> 25) & 0x3F) << 5)
        | (((w >>> 8) & 0xF) << 1);
    final int immJ = ((w >> 31) << 20) | (((w >>> 12) & 0xFF) << 12) | (((w >>> 20) & 1) << 11)
        | (((w >>> 21) & 0x3FF) << 1);
    final String d = REG[rd];
    final String s1 = REG[rs1];
    final String s2 = REG[rs2];
    switch (opcode) {
      case 0x37:
        return "lui " + d + "," + hex((w >>> 12) & 0xFFFFF);
      case 0x17:
        return "auipc " + d + "," + hex((w >>> 12) & 0xFFFFF);
      case 0x6F: {
        final var target = hex(pc + immJ);
        if (rd == 0) return "j " + target;
        if (rd == 1) return "jal " + target;
        return "jal " + d + "," + target;
      }
      case 0x67:
        if (f3 != 0) return null;
        if (rd == 0 && rs1 == 1 && immI == 0) return "ret";
        if (rd == 0) return immI == 0 ? "jr " + s1 : "jr " + immI + "(" + s1 + ")";
        if (rd == 1) return immI == 0 ? "jalr " + s1 : "jalr " + immI + "(" + s1 + ")";
        return immI == 0 ? "jalr " + d + "," + s1 : "jalr " + d + "," + immI + "(" + s1 + ")";
      case 0x63: {
        final var target = hex(pc + immB);
        switch (f3) {
          case 0:
            return rs2 == 0 ? "beqz " + s1 + "," + target : "beq " + s1 + "," + s2 + "," + target;
          case 1:
            return rs2 == 0 ? "bnez " + s1 + "," + target : "bne " + s1 + "," + s2 + "," + target;
          case 4:
            if (rs2 == 0) return "bltz " + s1 + "," + target;
            if (rs1 == 0) return "bgtz " + s2 + "," + target;
            return "blt " + s1 + "," + s2 + "," + target;
          case 5:
            if (rs1 == 0) return "blez " + s2 + "," + target;
            if (rs2 == 0) return "bgez " + s1 + "," + target;
            return "bge " + s1 + "," + s2 + "," + target;
          case 6:
            return "bltu " + s1 + "," + s2 + "," + target;
          case 7:
            return "bgeu " + s1 + "," + s2 + "," + target;
          default:
            return null;
        }
      }
      case 0x03: {
        final String[] names = {"lb", "lh", "lw", null, "lbu", "lhu", null, null};
        return names[f3] == null ? null : names[f3] + " " + d + "," + immI + "(" + s1 + ")";
      }
      case 0x23: {
        final String[] names = {"sb", "sh", "sw", null, null, null, null, null};
        return names[f3] == null ? null : names[f3] + " " + s2 + "," + immS + "(" + s1 + ")";
      }
      case 0x13:
        switch (f3) {
          case 0:
            if (rd == 0 && rs1 == 0 && immI == 0) return "nop";
            if (rs1 == 0) return "li " + d + "," + immI;
            if (immI == 0) return "mv " + d + "," + s1;
            return "addi " + d + "," + s1 + "," + immI;
          case 2:
            return "slti " + d + "," + s1 + "," + immI;
          case 3:
            return immI == 1 ? "seqz " + d + "," + s1 : "sltiu " + d + "," + s1 + "," + immI;
          case 4:
            return immI == -1 ? "not " + d + "," + s1 : "xori " + d + "," + s1 + "," + immI;
          case 6:
            return "ori " + d + "," + s1 + "," + immI;
          case 7:
            return "andi " + d + "," + s1 + "," + immI;
          case 1:
            return f7 == 0 ? "slli " + d + "," + s1 + "," + hex(rs2) : null;
          default: // 5
            if (f7 == 0) return "srli " + d + "," + s1 + "," + hex(rs2);
            if (f7 == 0x20) return "srai " + d + "," + s1 + "," + hex(rs2);
            return null;
        }
      case 0x33:
        return decodeRegister(f7, f3, d, s1, s2, rs1, rs2);
      case 0x0F:
        return decodeFence(w, f3, rd, rs1);
      case 0x73:
        return decodeSystem(w, f3, rd, rs1, rs2, f7, d, s1, s2);
      default:
        return null;
    }
  }

  private static String decodeRegister(int f7, int f3, String d, String s1, String s2, int rs1, int rs2) {
    if (f7 == 1) {
      final String[] names = {"mul", "mulh", "mulhsu", "mulhu", "div", "divu", "rem", "remu"};
      return names[f3] + " " + d + "," + s1 + "," + s2;
    }
    if (f7 == 0x20) {
      if (f3 == 0) return rs1 == 0 ? "neg " + d + "," + s2 : "sub " + d + "," + s1 + "," + s2;
      if (f3 == 5) return "sra " + d + "," + s1 + "," + s2;
      return null;
    }
    if (f7 != 0) return null;
    switch (f3) {
      case 2:
        if (rs2 == 0) return "sltz " + d + "," + s1;
        if (rs1 == 0) return "sgtz " + d + "," + s2;
        break;
      case 3:
        if (rs1 == 0) return "snez " + d + "," + s2;
        break;
      default:
        break;
    }
    final String[] names = {"add", "sll", "slt", "sltu", "xor", "srl", "or", "and"};
    return names[f3] + " " + d + "," + s1 + "," + s2;
  }

  /** The FENCE's predecessor or successor set: the letters of i, o, r, w, as binutils writes them. */
  private static String fenceSet(int bits) {
    if (bits == 0) return "unknown";
    final var set = new StringBuilder();
    if ((bits & 8) != 0) set.append('i');
    if ((bits & 4) != 0) set.append('o');
    if ((bits & 2) != 0) set.append('r');
    if ((bits & 1) != 0) set.append('w');
    return set.toString();
  }

  private static String decodeFence(int w, int f3, int rd, int rs1) {
    if (f3 == 1) return (w & 0xFFFFFFFF) == 0x0000100F ? "fence.i" : null;
    if (w == 0x8330000F) return "fence.tso";
    if (f3 != 0 || rd != 0 || rs1 != 0 || (w >>> 28) != 0) return null;
    final int pred = (w >>> 24) & 0xF;
    final int succ = (w >>> 20) & 0xF;
    if (pred == 0xF && succ == 0xF) return "fence";
    return "fence " + fenceSet(pred) + "," + fenceSet(succ);
  }

  private static String decodeSystem(int w, int f3, int rd, int rs1, int rs2, int f7, String d, String s1,
      String s2) {
    if (f3 == 0) {
      switch (w) {
        case 0x00000073:
          return "ecall";
        case 0x00100073:
          return "ebreak";
        case 0x30200073:
          return "mret";
        case 0x10200073:
          return "sret";
        case 0x10500073:
          return "wfi";
        default:
          break;
      }
      if (f7 == 0x09 && rd == 0) {
        if (rs2 != 0) return "sfence.vma " + s1 + "," + s2;
        return rs1 != 0 ? "sfence.vma " + s1 : "sfence.vma";
      }
      return null;
    }
    if (f3 == 4) return null;
    if (w == 0xC0001073) return "unimp";   // csrrw zero,cycle,zero: binutils' unimp
    final var csr = csrName((w >>> 20) & 0xFFF);
    final var src = (f3 & 4) != 0 ? String.valueOf(rs1) : s1;   // the immediate forms: a 5-bit uimm
    switch (f3 & 3) {
      case 1:
        if (rd == 0) return ((f3 & 4) != 0 ? "csrwi " : "csrw ") + csr + "," + src;
        return ((f3 & 4) != 0 ? "csrrwi " : "csrrw ") + d + "," + csr + "," + src;
      case 2:
        if ((f3 & 4) == 0 && rs1 == 0) return "csrr " + d + "," + csr;
        if (rd == 0) return ((f3 & 4) != 0 ? "csrsi " : "csrs ") + csr + "," + src;
        return ((f3 & 4) != 0 ? "csrrsi " : "csrrs ") + d + "," + csr + "," + src;
      default: // 3
        if (rd == 0) return ((f3 & 4) != 0 ? "csrci " : "csrc ") + csr + "," + src;
        return ((f3 & 4) != 0 ? "csrrci " : "csrrc ") + d + "," + csr + "," + src;
    }
  }
}
