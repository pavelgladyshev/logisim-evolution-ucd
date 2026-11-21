package com.cburch.logisim.riscv.cpu.gdb;

import com.cburch.logisim.riscv.cpu.csrs.MMCSR;
import com.cburch.logisim.riscv.cpu.csrs.SCSR;
import com.cburch.logisim.riscv.cpu.rv32imData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The registers the GDB server shows gdb, with the numbers that OpenOCD gives the same CPU on an FPGA (and gdb's
 * RISC-V target, riscv-tdep): x0-x31 are 0-31, the pc 32, CSR n is 65 + n, and the virtual register priv (the
 * hart's privilege mode: 0 U, 1 S, 3 M) 4161. In gdb's remote protocol a register's value is its bytes in the
 * target's order, little-endian here, two hex digits each; "xx" for each byte of a register the target cannot
 * read tells gdb it is unavailable (gdb manual, "Packets", the p packet).
 *
 * Every method that reads or writes the CPU runs on the simulation thread, inside a debugger request.
 */
public final class GdbRegisters {

    public static final int PC = 32;
    public static final int FIRST_CSR = 65;
    public static final int LAST_CSR = FIRST_CSR + 4095;
    public static final int PRIV = 4161;

    /** The integer registers, x0 to x31, by the names of gdb's own riscv-32bit-cpu.xml (x8 is fp, or s0). */
    public static final String[] X_NAMES = {
        "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2", "fp", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
        "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"};

    /**
     * The CSRs the CPU implements, by name, in address order: the registers the Java model and its Verilog twin
     * (riscvCpu.v, csrValue) give their own storage and behaviour, and the two scratch registers. Every other CSR
     * address of the model is plain storage (or reads 0), with no effect on the hart, so gdb is not shown it.
     */
    public static final Map<String, Integer> CSRS;

    static {
        final var csrs = new LinkedHashMap<String, Integer>();
        for (final var csr : new SCSR[] {SCSR.SSTATUS, SCSR.SIE, SCSR.STVEC, SCSR.SSCRATCH, SCSR.SEPC, SCSR.SCAUSE,
            SCSR.STVAL, SCSR.SIP, SCSR.SATP}) {
            csrs.put(csr.name().toLowerCase(), csr.getAddress());
        }
        for (final var csr : new MMCSR[] {MMCSR.MSTATUS, MMCSR.MEDELEG, MMCSR.MIDELEG, MMCSR.MIE, MMCSR.MTVEC,
            MMCSR.MSCRATCH, MMCSR.MEPC, MMCSR.MCAUSE, MMCSR.MTVAL, MMCSR.MIP}) {
            csrs.put(csr.name().toLowerCase(), csr.getAddress());
        }
        CSRS = Collections.unmodifiableMap(csrs);
    }

    /** Whether gdb's register regnum is one this server knows (whether the CPU implements it or not). */
    public static boolean exists(int regnum) {
        return (regnum >= 0 && regnum <= PC) || (regnum >= FIRST_CSR && regnum <= LAST_CSR) || regnum == PRIV;
    }

    /** Whether the CSR at this address is one of {@link #CSRS}. */
    public static boolean isImplementedCsr(int address) {
        return CSRS.containsValue(address);
    }

    /**
     * The reply to the packet p for register regnum: its value, "xx" for each byte if the CPU does not implement it,
     * or an error for a number this server does not know.
     */
    public static String read(rv32imData cpu, int regnum) {
        if (regnum >= 0 && regnum < PC) return formatWord(cpu.getX(regnum));
        if (regnum == PC) return formatWord(cpu.getPC().get());
        if (regnum == PRIV) return formatWord(cpu.getCurrentPrivilegeMode().getValue());
        if (regnum >= FIRST_CSR && regnum <= LAST_CSR) {
            final int address = regnum - FIRST_CSR;
            return isImplementedCsr(address) ? formatWord(cpu.getCSRValue(address)) : "xxxxxxxx";
        }
        return "E00";
    }

    /**
     * Writes a register for the packet P and returns the reply: "OK", or an error for priv (the privilege mode is
     * not gdb's to change), a CSR the CPU does not implement or a number this server does not know. A CSR is written
     * as a CSR instruction would write it, its read-only fields and views (sstatus, sie, sip) included.
     */
    public static String write(rv32imData cpu, int regnum, long value) {
        if (regnum >= 0 && regnum < PC) {
            cpu.setX(regnum, value);
        } else if (regnum == PC) {
            cpu.getPC().set(value & 0xFFFFFFFFL);
        } else if (regnum >= FIRST_CSR && regnum <= LAST_CSR && isImplementedCsr(regnum - FIRST_CSR)) {
            cpu.setCSR(regnum - FIRST_CSR, value & 0xFFFFFFFFL);
        } else {
            return "E01";
        }
        return "OK";
    }

    /** The value of the packet P's hex digits: the register's bytes, little-endian (at most 4 are used). */
    public static long parseValue(String hex) {
        long value = 0;
        for (int i = 0; i + 1 < hex.length() && i < 8; i += 2) {
            value |= Long.parseLong(hex.substring(i, i + 2), 16) << (4 * i);
        }
        return value;
    }

    /** A 32-bit word as gdb wants it: its four bytes, least significant first. */
    public static String formatWord(long value) {
        return String.format("%08x", Integer.reverseBytes((int) value));
    }

    private GdbRegisters() {}
}
