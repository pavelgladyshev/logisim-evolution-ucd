package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.csrs.*;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExceptionTest {
    rv32imData cpu;

    MTVEC_CSR mtvec;
    MCAUSE_CSR mcause;
    MSTATUS_CSR mstatus;

    @BeforeEach
    void setup(){
        //create new CPU state
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, rv32imData.CPUState.RUNNING, null);
        mtvec = (MTVEC_CSR) MMCSR.getCSR(cpu, MTVEC);
        mcause = (MCAUSE_CSR) MMCSR.getCSR(cpu, MCAUSE);
        mstatus = (MSTATUS_CSR) MMCSR.getCSR(cpu, MSTATUS);
    }

    @Test
    void instructionTest_ebreak(){
        // perform ebreak
        cpu.update(0x100073,0,0, 0);
        assertEquals(0x400000, MMCSR.getValue(cpu, MEPC));
        assertEquals(mtvec.BASE.get(), cpu.getPC().get());
        assertEquals(mcause.INTERRUPT.get(), 0);
        assertEquals(mcause.EXCEPTION_CODE.get(), MCAUSE_CSR.TRAP_CAUSE.BREAKPOINT.getExceptionCode());
        assertEquals(MMCSR.getValue(cpu, MTVAL), 0);
        assertEquals(mstatus.MPP.get(), PRIVILEGE_MODE.MACHINE.getValue());
    }

    //tests ecall from machine mode as default (starting cpu privilege mode)
    @Test
    void instructionTest_ecall(){
        // perform ecall
        cpu.update(0x73,0,0, 0);
        assertEquals(0x400000, MMCSR.getValue(cpu, MEPC));
        assertEquals(mtvec.BASE.get(), cpu.getPC().get());
        assertEquals(mcause.INTERRUPT.get(), 0);
        assertEquals(mcause.EXCEPTION_CODE.get(), MCAUSE_CSR.TRAP_CAUSE.ENVIRONMENT_CALL_FROM_M_MODE.getExceptionCode());
        assertEquals(MMCSR.getValue(cpu, MTVAL), 0);
        assertEquals(mstatus.MPP.get(), PRIVILEGE_MODE.MACHINE.getValue());
    }

    @Test
    void exceptionTest_illegal_instruction_unknown() {
        // illegal instruction passed to cpu
        cpu.update(0x024859348,0,0, 0);
        assertEquals(0x400000, MMCSR.getValue(cpu, MEPC));
        assertEquals(mtvec.BASE.get(), cpu.getPC().get());
        assertEquals(mcause.INTERRUPT.get(), 0);
        assertEquals(mcause.EXCEPTION_CODE.get(), MCAUSE_CSR.TRAP_CAUSE.ILLEGAL_INSTRUCTION.getExceptionCode());
        assertEquals(MMCSR.getValue(cpu, MTVAL), 0);
        assertEquals(mstatus.MPP.get(), PRIVILEGE_MODE.MACHINE.getValue());
    }
}


