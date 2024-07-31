package com.cburch.logisim.riscv;

import static com.cburch.logisim.riscv.MMCSR.*;

public class TrapHandler {

    public static void handle(rv32imData hartData, MCAUSE_CSR.TRAP_CAUSE cause) {

        MSTATUS_CSR mstatus = (MSTATUS_CSR) MMCSR.getCSR(hartData, MSTATUS);
        MEPC_CSR mepc = (MEPC_CSR) MMCSR.getCSR(hartData, MEPC);
        MTVEC_CSR mtvec = (MTVEC_CSR) MMCSR.getCSR(hartData, MTVEC);
        MCAUSE_CSR mcause = (MCAUSE_CSR) MMCSR.getCSR(hartData, MCAUSE);
        CSR mtval = MMCSR.getCSR(hartData, MTVAL);
        ProgramCounter pc = hartData.getPC();

        // mepc = PC ( points to instruction that caused the exception / instruction to resume after interrupt )
        mepc.write(pc.get());
        // PC = mtvec
        if( cause.isInterrupt() && ((MTVEC_CSR.MODE) mtvec.MODE).isVectored() ) {
            pc.set((mtvec.BASE.get() + 4) * cause.getExceptionCode());
        }
        else {
            pc.set(mtvec.BASE.get());
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

        MMCSR.getCSR(hartData, MIP).write(MMCSR.getCSR(hartData, MIP).read() & (~0x80));
        MMCSR.getCSR(hartData, MIE).write(MMCSR.getCSR(hartData, MIE).read() & (~0x80));
    }

    public static void throwIllegalInstructionException(rv32imData hartData) {
        TrapHandler.handle(hartData, MCAUSE_CSR.TRAP_CAUSE.ILLEGAL_INSTRUCTION);
    }
}
