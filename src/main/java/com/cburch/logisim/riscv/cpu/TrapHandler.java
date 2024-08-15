package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.csrs.*;
import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.*;

public class TrapHandler {

    public static void handle(rv32imData hartData, MCAUSE_CSR.TRAP_CAUSE cause) {

        MSTATUS_CSR mstatus = (MSTATUS_CSR) MMCSR.getCSR(hartData, MSTATUS);
        MEPC_CSR mepc = (MEPC_CSR) MMCSR.getCSR(hartData, MEPC);
        MTVEC_CSR mtvec = (MTVEC_CSR) MMCSR.getCSR(hartData, MTVEC);
        MCAUSE_CSR mcause = (MCAUSE_CSR) MMCSR.getCSR(hartData, MCAUSE);
        CSR mtval = MMCSR.getCSR(hartData, MTVAL);
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
