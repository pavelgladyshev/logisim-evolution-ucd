package com.cburch.logisim.riscv;

import com.cburch.logisim.data.*;
import com.cburch.logisim.riscv.cpu.rv32imData;
import com.cburch.logisim.riscv.cpu.csrs.MMCSR;
import com.cburch.logisim.riscv.cpu.csrs.PRIVILEGE_MODE;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static java.lang.System.out;

public class rv32imDataTest {

    @Test
    void defaultConstructor_shouldProduceZeroedState() {
        out.println("rv32imData test: default constructor should produce zeroed initial state with specified initial PC value");

        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        out.printf("\tTesting newly created CPU for initial values ...\n");

        long[] expected = new long[32];
        assertRegistersEqual(cpu, expected);
    }

    @Test
    void reset_followsTheRiscvSpec() {
        out.println("rv32imData test: the Reset pin as the RISC-V privileged spec says, the rest of the state kept");
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        final int mstatus = MMCSR.MSTATUS.getAddress();
        cpu.setX(5, 42);
        cpu.setCSR(mstatus, (1L << 3) | (1L << 17) | (1L << 18));   // MIE, MPRV, SUM
        cpu.setCSR(MMCSR.MCAUSE.getAddress(), 7);
        cpu.setCSR(MMCSR.MTVEC.getAddress(), 0x100);
        cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
        cpu.getPC().set(0x1234);
        cpu.reset(0x2000, 1234, false);
        assertEquals(0x2000, cpu.getPC().get(), "pc at the reset vector");
        assertEquals(PRIVILEGE_MODE.MACHINE, cpu.getCurrentPrivilegeMode(), "M-mode");
        assertEquals(0, cpu.getCSRValue(mstatus) & (1L << 3), "mstatus.MIE 0");
        assertEquals(0, cpu.getCSRValue(mstatus) & (1L << 17), "mstatus.MPRV 0");
        assertEquals(1L << 18, cpu.getCSRValue(mstatus) & (1L << 18), "mstatus.SUM kept");
        assertEquals(0, cpu.getCSRValue(MMCSR.MCAUSE.getAddress()), "mcause 0");
        assertEquals(0x100, cpu.getCSRValue(MMCSR.MTVEC.getAddress()), "mtvec kept");
        assertEquals(42, cpu.getX(5), "x5 kept");
    }

    @Test
    void tlbEntries_areTheCpusAttribute() {
        out.println("rv32imData test: the TLB has 128 entries unless the CPU says otherwise");
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        assertEquals(128, cpu.getTlb().getSize());
        rv32imData small = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING,
            null, 16);
        assertEquals(16, small.getTlb().getSize());
    }

    /** The registers hold 32 bits: compared as such (an expected 0xffffffffL is the register's -1). */
    public static void assertRegistersEqual(rv32imData tested, long[] expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals((int) expected[i], (int) tested.getX(i), "Register x" + i + " did not match expected value!");
        }
    }

    /** Verify values of all registers from startIndex to endIndex against expected, as 32-bit values.*/
    public static void verifyRegisterRange(rv32imData cpu, long[] expected, int startIndex, int endIndex) {
        for (int i = startIndex; i <= endIndex; i++) {
            assertEquals((int) expected[i], (int) cpu.getX(i), "Register x" + i + " did not match expected value!");
        }
    }
}