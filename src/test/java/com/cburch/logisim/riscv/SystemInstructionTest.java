package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.SystemInstruction;
import com.cburch.logisim.riscv.cpu.csrs.MMCSR;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SystemInstructionTest {
    rv32imData cpu;

    @BeforeEach
    void setup() {
        // Create new CPU state
        cpu = new rv32imData(Value.FALSE, 0x400000);
    }

    @Test
    void instructionTest_csrrw() {
        // Set CSR value and register values before executing
        MMCSR.getCSR(cpu, MSTATUS).write(0x1234);
        cpu.setX(1, 0x5678); // rs1 value

        cpu.update(0x00100073); // csrrw rd, csr, rs1
        SystemInstruction.execute(cpu);

        assertEquals(0x1234, cpu.getX(0)); // rd should contain old CSR value
        assertEquals(0x5678, MMCSR.getValue(cpu, MSTATUS)); // CSR should be updated to rs1 value
    }

    @Test
    void instructionTest_csrrs() {
        // Set CSR value and register values before executing
        MMCSR.getCSR(cpu, MSTATUS).write(0x1234);
        cpu.setX(1, 0x5678); // rs1 value

        cpu.update(0x00200073); // csrrs rd, csr, rs1
        SystemInstruction.execute(cpu);

        assertEquals(0x1234, cpu.getX(0)); // rd should contain old CSR value
        assertEquals(0x1234 | 0x5678, MMCSR.getValue(cpu, MSTATUS)); // CSR should be OR-ed with rs1 value
    }

    @Test
    void instructionTest_csrrc() {
        // Set CSR value and register values before executing
        MMCSR.getCSR(cpu, MSTATUS).write(0xFFFF);
        cpu.setX(1, 0x0F0F); // rs1 value

        cpu.update(0x00300073); // csrrc rd, csr, rs1
        SystemInstruction.execute(cpu);

        assertEquals(0xFFFF, cpu.getX(0)); // rd should contain old CSR value
        assertEquals(0xFFFF & ~0x0F0F, MMCSR.getValue(cpu, MSTATUS)); // CSR should be AND-ed with NOT(rs1) value
    }

    @Test
    void instructionTest_csrrwi() {
        // Set CSR value and register values before executing
        MMCSR.getCSR(cpu, MSTATUS).write(0x1234);
        cpu.setX(1, 0x5678); // imm value

        cpu.update(0x10000073); // csrrwi rd, csr, imm
        SystemInstruction.execute(cpu);

        assertEquals(0x1234, cpu.getX(0)); // rd should contain old CSR value
        assertEquals(0x5678, MMCSR.getValue(cpu, MSTATUS)); // CSR should be updated to imm value
    }

    @Test
    void instructionTest_csrrsi() {
        // Set CSR value and register values before executing
        MMCSR.getCSR(cpu, MSTATUS).write(0x1234);
        cpu.setX(1, 0x5678); // imm value

        cpu.update(0x20000073); // csrrsi rd, csr, imm
        SystemInstruction.execute(cpu);

        assertEquals(0x1234, cpu.getX(0)); // rd should contain old CSR value
        assertEquals(0x1234 | 0x5678, MMCSR.getValue(cpu, MSTATUS)); // CSR should be OR-ed with imm value
    }

    @Test
    void instructionTest_csrrci() {
        // Set CSR value and register values before executing
        MMCSR.getCSR(cpu, MSTATUS).write(0xFFFF);
        cpu.setX(1, 0x0F0F); // imm value

        cpu.update(0x30000073); // csrrci rd, csr, imm
        SystemInstruction.execute(cpu);

        assertEquals(0xFFFF, cpu.getX(0)); // rd should contain old CSR value
        assertEquals(0xFFFF & ~0x0F0F, MMCSR.getValue(cpu, MSTATUS)); // CSR should be AND-ed with NOT(imm) value
    }

    @Test
    void instructionTest_mret() {
        // Set CSR values and register values before executing
        MMCSR.getCSR(cpu, MEPC).write(0x1000);
        MMCSR.getCSR(cpu, MSTATUS).write(0x1800); // MPP=0b11, MPIE=1

        cpu.update(0x30200073); // mret instruction
        SystemInstruction.execute(cpu);

        assertEquals(0x1000, cpu.getPC().get()); // PC should be set to MEPC value
        assertEquals(0x1800 & ~0x800, MMCSR.getValue(cpu, MSTATUS)); // MPP should be restored to previous value
        assertEquals(0x1800 | 0x800, MMCSR.getValue(cpu, MSTATUS)); // MPIE should be set
    }
}
