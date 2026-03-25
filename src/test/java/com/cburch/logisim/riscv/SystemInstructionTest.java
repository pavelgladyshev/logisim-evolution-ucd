package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.SystemInstruction;
import com.cburch.logisim.riscv.cpu.csrs.*;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SystemInstructionTest {
    rv32imData cpu;
    MCAUSE_CSR mcause;
    MSTATUS_CSR mstatus;

    @BeforeEach
    void setup() {
        // Create new CPU state
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        mcause = (MCAUSE_CSR) MMCSR.getCSR(cpu, MCAUSE);
        mstatus = (MSTATUS_CSR) MMCSR.getCSR(cpu, MSTATUS);
    }

    @Test
    void instructionTest_csrrw() {
        cpu.update(0xbc40413,0,0, 0);
        cpu.setX(8, 0x5678);
        cpu.update(0x30541073,0,0, 0);

        assertEquals(0x5678, cpu.getX(8));
        assertEquals(0x1800, MMCSR.getValue(cpu, MSTATUS));
        assertEquals(0, mcause.INTERRUPT.get());
        assertEquals(0, mcause.EXCEPTION_CODE.get());
    }

    @Test
    void instructionTest_csrrs() {
        cpu.setX(8, 0x5678);
        cpu.update(0x3002a373,0,0, 0);

        assertEquals(0x5678, cpu.getX(8));
        assertEquals(0x1800, MMCSR.getValue(cpu, MSTATUS));
        assertEquals(0, mcause.INTERRUPT.get());
        assertEquals(0, mcause.EXCEPTION_CODE.get());
    }

    @Test
    void instructionTest_csrrc() {
        // Set CSR value and register values before executing
        cpu.setX(1, 0x0F0F); // rs1 value

        // csrrc x0, mstatus, x1 (0x300210f3)
        cpu.update(0x3002b573,0,0, 0);

        assertEquals(0, cpu.getX(0)); // rd should contain old CSR value
        assertEquals(6144, MMCSR.getValue(cpu, MSTATUS)); // CSR should be AND-ed with NOT(rs1) value
    }

    @Test
    void instructionTest_csrrwi() {
        cpu.setX(5, 0x5678);
        cpu.update(0x3002d073,0,0, 0);

        assertEquals(0x5678, cpu.getX(5));
        assertEquals(0, mcause.INTERRUPT.get());
        assertEquals(0, mcause.EXCEPTION_CODE.get());
    }

    @Test
    void instructionTest_csrrsi() {
        cpu.setX(8, 0x5678);
        cpu.update(0x30046073,0,0, 0);

        assertEquals(0x5678, cpu.getX(8));
        assertEquals(0x1808, MMCSR.getValue(cpu, MSTATUS));
        assertEquals(0, mcause.INTERRUPT.get());
        assertEquals(0, mcause.EXCEPTION_CODE.get());
    }

    @Test
    void instructionTest_csrrci() {
        cpu.setX(8, 0x5678);
        cpu.update(0x30047073,0,0, 0);

        assertEquals(0x5678, cpu.getX(8));
        assertEquals(0x1800, MMCSR.getValue(cpu, MSTATUS));
        assertEquals(0, mcause.INTERRUPT.get());
        assertEquals(0, mcause.EXCEPTION_CODE.get());

    }

    @Test
    void instructionTest_mret() {
        // Set CSR values before executing
        MMCSR.getCSR(cpu, MEPC).write(0x1000);
        // MPP=0b11(Machine), MPIE=1(bit7=0x80), MIE=0 → 0x1880
        MMCSR.getCSR(cpu, MSTATUS).write(0x1880);

        // mret instruction (cpu.update decodes and executes it)
        cpu.update(0x30200073,0,0, 0);

        // PC should be set to MEPC value
        assertEquals(0x1000, cpu.getPC().get());
        // After mret: MIE ← MPIE(1)=bit3, MPIE ← 1=bit7, MPP ← USER(0)
        // Expected: 0x08 (MIE) | 0x80 (MPIE) = 0x88
        assertEquals(0x88, MMCSR.getValue(cpu, MSTATUS));
        // Privilege mode should be restored to Machine (from MPP=0b11)
        assertEquals(PRIVILEGE_MODE.MACHINE, cpu.getCurrentPrivilegeMode());
    }
}
