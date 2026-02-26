package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.ControlAndStatusRegisters;
import com.cburch.logisim.riscv.cpu.rv32imData;
import com.cburch.logisim.riscv.cpu.csrs.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CSR access control: read-only protection, write suppression,
 * privilege checks, and correct exception handling.
 */
class CSRAccessTest {

    rv32imData cpu;
    MCAUSE_CSR mcause;
    MSTATUS_CSR mstatus;

    // CSR addresses
    static final int CSR_MSTATUS = 0x300;  // Machine RW
    static final int CSR_CYCLE   = 0xC00;  // User read-only counter

    // MCAUSE exception code for illegal instruction
    static final int ILLEGAL_INSTRUCTION_CODE = 2;

    @BeforeEach
    void setup() {
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false,
                rv32imData.CPUState.RUNNING, null);
        mcause = (MCAUSE_CSR) MMCSR.getCSR(cpu, MCAUSE);
        mstatus = (MSTATUS_CSR) MMCSR.getCSR(cpu, MSTATUS);

        // Set MTVEC to a known value so we can verify PC after exceptions
        MMCSR.getCSR(cpu, MTVEC).write(0x1000);
    }

    /**
     * Encode a CSR instruction.
     * Format: csr[31:20] | rs1[19:15] | funct3[14:12] | rd[11:7] | opcode[6:0]
     */
    static long csrInstruction(int csr, int rs1, int funct3, int rd) {
        return ((long)(csr & 0xFFF) << 20) | ((rs1 & 0x1F) << 15) |
                ((funct3 & 0x7) << 12) | ((rd & 0x1F) << 7) | 0x73;
    }

    // ========== isReadOnly tests ==========

    @Test
    void testIsReadOnly() {
        // CSR addresses with bits [11:10] == 0x3 are read-only
        assertTrue(ControlAndStatusRegisters.isReadOnly(0xC00));   // CYCLE
        assertTrue(ControlAndStatusRegisters.isReadOnly(0xC01));   // TIME
        assertTrue(ControlAndStatusRegisters.isReadOnly(0xC02));   // INSTRET
        assertTrue(ControlAndStatusRegisters.isReadOnly(0xF14));   // MHARTID

        // CSR addresses with bits [11:10] != 0x3 are read-write
        assertFalse(ControlAndStatusRegisters.isReadOnly(0x300));  // MSTATUS
        assertFalse(ControlAndStatusRegisters.isReadOnly(0x305));  // MTVEC
        assertFalse(ControlAndStatusRegisters.isReadOnly(0x180));  // SATP
        assertFalse(ControlAndStatusRegisters.isReadOnly(0x341));  // MEPC
    }

    // ========== Write suppression for CSRRS/CSRRC with rs1=x0 ==========

    @Test
    void testCsrrsWithRs1ZeroOnReadOnlyCSR() {
        // csrrs x5, cycle, x0  — this is the "csrr x5, cycle" pseudo-instruction
        // Should succeed: rs1=x0 means no write, so read-only CSR is fine
        long instr = csrInstruction(CSR_CYCLE, 0, 0x2, 5);
        cpu.update(instr, 0, 0, 0);

        // No exception should have occurred
        assertEquals(0, mcause.EXCEPTION_CODE.get());
        // PC should have advanced past the instruction (0x400000 + 4)
        assertEquals(0x400004, cpu.getPC().get());
    }

    @Test
    void testCssrsiWithUimmZeroOnReadOnlyCSR() {
        // csrrsi x5, cycle, 0  — uimm=0 means no write
        // Should succeed on read-only CSR
        long instr = csrInstruction(CSR_CYCLE, 0, 0x6, 5);
        cpu.update(instr, 0, 0, 0);

        assertEquals(0, mcause.EXCEPTION_CODE.get());
        assertEquals(0x400004, cpu.getPC().get());
    }

    @Test
    void testCsrrciWithUimmZeroOnReadOnlyCSR() {
        // csrrci x5, cycle, 0  — uimm=0 means no write
        // Should succeed on read-only CSR
        long instr = csrInstruction(CSR_CYCLE, 0, 0x7, 5);
        cpu.update(instr, 0, 0, 0);

        assertEquals(0, mcause.EXCEPTION_CODE.get());
        assertEquals(0x400004, cpu.getPC().get());
    }

    @Test
    void testCssrcWithRs1ZeroOnReadOnlyCSR() {
        // csrrc x5, cycle, x0  — rs1=x0 means no write
        // Should succeed on read-only CSR
        long instr = csrInstruction(CSR_CYCLE, 0, 0x3, 5);
        cpu.update(instr, 0, 0, 0);

        assertEquals(0, mcause.EXCEPTION_CODE.get());
        assertEquals(0x400004, cpu.getPC().get());
    }

    // ========== Write to read-only CSR raises exception ==========

    @Test
    void testCsrrwToReadOnlyCSRRaisesException() {
        // csrrw x5, cycle, x1  — CSRRW always writes → illegal on read-only CSR
        cpu.setX(1, 42);
        long instr = csrInstruction(CSR_CYCLE, 1, 0x1, 5);
        cpu.update(instr, 0, 0, 0);

        assertEquals(ILLEGAL_INSTRUCTION_CODE, mcause.EXCEPTION_CODE.get());
        // PC should be at MTVEC (0x1000), not MTVEC+4
        assertEquals(0x1000, cpu.getPC().get());
    }

    @Test
    void testCsrrsWithNonzeroRs1ToReadOnlyCSRRaisesException() {
        // csrrs x5, cycle, x1  — rs1 != x0, so this writes → illegal on read-only CSR
        cpu.setX(1, 42);
        long instr = csrInstruction(CSR_CYCLE, 1, 0x2, 5);
        cpu.update(instr, 0, 0, 0);

        assertEquals(ILLEGAL_INSTRUCTION_CODE, mcause.EXCEPTION_CODE.get());
        assertEquals(0x1000, cpu.getPC().get());
    }

    @Test
    void testCsrrsiWithNonzeroUimmToReadOnlyCSRRaisesException() {
        // csrrsi x5, cycle, 1  — uimm=1, so this writes → illegal on read-only CSR
        long instr = csrInstruction(CSR_CYCLE, 1, 0x6, 5);
        cpu.update(instr, 0, 0, 0);

        assertEquals(ILLEGAL_INSTRUCTION_CODE, mcause.EXCEPTION_CODE.get());
        assertEquals(0x1000, cpu.getPC().get());
    }

    @Test
    void testCsrrwiToReadOnlyCSRRaisesException() {
        // csrrwi x5, cycle, 1  — CSRRWI always writes → illegal on read-only CSR
        long instr = csrInstruction(CSR_CYCLE, 1, 0x5, 5);
        cpu.update(instr, 0, 0, 0);

        assertEquals(ILLEGAL_INSTRUCTION_CODE, mcause.EXCEPTION_CODE.get());
        assertEquals(0x1000, cpu.getPC().get());
    }

    // ========== Exception PC correctness ==========

    @Test
    void testExceptionSetsCorrectMEPC() {
        // Verify MEPC is set to the faulting instruction's PC, not PC+4
        long instr = csrInstruction(CSR_CYCLE, 1, 0x1, 5);
        cpu.update(instr, 0, 0, 0);

        assertEquals(ILLEGAL_INSTRUCTION_CODE, mcause.EXCEPTION_CODE.get());
        // MEPC should be the PC of the faulting instruction (initial PC = 0x400000)
        assertEquals(0x400000, MMCSR.getValue(cpu, MEPC));
        // PC should be at MTVEC, not MTVEC+4
        assertEquals(0x1000, cpu.getPC().get());
    }

    // ========== Privilege level tests ==========

    @Test
    void testMachineModeCanAccessMachineCSR() {
        // CPU starts in machine mode (MPP=3). Access to MSTATUS should succeed.
        long instr = csrInstruction(CSR_MSTATUS, 0, 0x2, 5);  // csrrs x5, mstatus, x0
        cpu.update(instr, 0, 0, 0);

        assertEquals(0, mcause.EXCEPTION_CODE.get());
        assertEquals(0x400004, cpu.getPC().get());
    }

    @Test
    void testLowPrivilegeCannotAccessMachineCSR() {
        // Set MPP to USER mode (0) to simulate lower privilege
        mstatus.MPP.set(PRIVILEGE_MODE.USER.getValue());

        // csrrs x5, mstatus, x0  — reading MSTATUS requires machine privilege
        long instr = csrInstruction(CSR_MSTATUS, 0, 0x2, 5);
        cpu.update(instr, 0, 0, 0);

        assertEquals(ILLEGAL_INSTRUCTION_CODE, mcause.EXCEPTION_CODE.get());
        assertEquals(0x1000, cpu.getPC().get());
    }

    // ========== Normal CSR operations still work ==========

    @Test
    void testCsrrwToWritableCSR() {
        // csrrw x5, mstatus, x1  — writing to a writable CSR should work
        cpu.setX(1, 0x08);  // Set MIE bit
        long instr = csrInstruction(CSR_MSTATUS, 1, 0x1, 5);
        long oldMstatus = MMCSR.getValue(cpu, MSTATUS);
        cpu.update(instr, 0, 0, 0);

        // rd should get old value, CSR should get new value
        assertEquals(oldMstatus, cpu.getX(5));
        assertEquals(0x08, MMCSR.getValue(cpu, MSTATUS));
        assertEquals(0x400004, cpu.getPC().get());
    }

    @Test
    void testCsrrsWithRs1ZeroReadsButDoesNotWrite() {
        // csrrs x5, mstatus, x0  — should read but not write (even on RW CSR)
        long oldMstatus = MMCSR.getValue(cpu, MSTATUS);
        long instr = csrInstruction(CSR_MSTATUS, 0, 0x2, 5);
        cpu.update(instr, 0, 0, 0);

        // rd should get old value
        assertEquals(oldMstatus, cpu.getX(5));
        // CSR should be unchanged
        assertEquals(oldMstatus, MMCSR.getValue(cpu, MSTATUS));
    }
}
