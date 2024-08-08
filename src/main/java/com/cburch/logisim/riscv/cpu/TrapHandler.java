package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.csrs.*;
import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.*;

public class TrapHandler {

    public static void handle(rv32imData hartData, MCAUSE_CSR.TRAP_CAUSE cause) {

        MSTATUS mstatus = new MSTATUS(MMCSR.getValue(hartData, MMCSR.MSTATUS));
        MTVEC mtvec = new MTVEC(MMCSR.getValue(hartData, MMCSR.MTVEC));
        MCAUSE mcause = new MCAUSE(MMCSR.getValue(hartData, MMCSR.MCAUSE));
        CSR mtval = hartData.getCSR(MMCSR.MTVAL.getAddress());
        ProgramCounter pc = hartData.getPC();

        // mepc = PC ( points to instruction that caused the exception / instruction to resume after interrupt )
        hartData.setCSR(MMCSR.MEPC.getAddress(), pc.get());

        // PC = mtvec
        if( cause.isInterrupt() && (mtvec.MODE.get() == 1)) {
            pc.set((mtvec.BASE.get() + 4) * cause.getExceptionCode());
        }
        else {
            pc.set(MMCSR.getValue(hartData, MMCSR.MTVEC));
        }
        // mcause = exception cause
        mcause.INTERRUPT.set(cause.getInterrupt());
        mcause.EXCEPTION_CODE.set(cause.getExceptionCode());
        // mtval = faulting address/instruction/zero
        switch(cause){
            case INSTRUCTION_ACCESS_FAULT: {
                mtval.write(hartData.getIR().get());
                break;
            }
            default:
                mtval.write(0);
        }
        // mstatus.MPIE = mstatus.MIE
        mstatus.MPIE.set(mstatus.MIE.get());
        // mstatus.MIE = 0
        mstatus.MIE.set(0);
        // privilege mode = MACHINE ( 0b11 )
        mstatus.MPP.set(PRIVILEGE_MODE.MACHINE.getValue());
    }

    public static void throwIllegalInstructionException(rv32imData hartData) {
        TrapHandler.handle(hartData, MCAUSE_CSR.TRAP_CAUSE.ILLEGAL_INSTRUCTION);
    }
}
