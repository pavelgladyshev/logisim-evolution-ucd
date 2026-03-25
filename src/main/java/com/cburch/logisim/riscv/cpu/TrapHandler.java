package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.csrs.*;
import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.*;

public class TrapHandler {

    /**
     * Handle a trap (exception or interrupt).
     *
     * Per RISC-V spec, the trap is delegated to S-mode if:
     *   1. The current privilege mode is less than Machine (i.e., S-mode or U-mode), AND
     *   2. The corresponding bit in medeleg (for exceptions) or mideleg (for interrupts) is set.
     *
     * Otherwise, the trap is handled in M-mode.
     */
    public static void handle(rv32imData hartData, MCAUSE_CSR.TRAP_CAUSE cause) {

        MSTATUS_CSR mstatus = (MSTATUS_CSR) MMCSR.getCSR(hartData, MMCSR.MSTATUS);
        ProgramCounter pc = hartData.getPC();
        PRIVILEGE_MODE currentPriv = hartData.getCurrentPrivilegeMode();

        boolean isInterrupt = cause.isInterrupt();
        int code = cause.getExceptionCode();

        // Check if this trap should be delegated to S-mode
        boolean delegated = false;
        if (currentPriv != PRIVILEGE_MODE.MACHINE) {
            if (isInterrupt) {
                MIDELEG_CSR mideleg = (MIDELEG_CSR) MMCSR.getCSR(hartData, MMCSR.MIDELEG);
                delegated = mideleg.isDelegated(code);
            } else {
                MEDELEG_CSR medeleg = (MEDELEG_CSR) MMCSR.getCSR(hartData, MMCSR.MEDELEG);
                delegated = medeleg.isDelegated(code);
            }
        }

        if (delegated) {
            // ---- Trap to S-mode ----
            STVEC_CSR stvec = (STVEC_CSR) hartData.getCSR(SCSR.STVEC.getAddress());
            SCAUSE_CSR scause = (SCAUSE_CSR) hartData.getCSR(SCSR.SCAUSE.getAddress());
            CSR stval = hartData.getCSR(SCSR.STVAL.getAddress());

            // sepc = PC
            hartData.setCSR(SCSR.SEPC.getAddress(), pc.get());

            // PC = stvec (vectored or direct)
            if (isInterrupt && (stvec.MODE.get() == 1)) {
                pc.set((stvec.BASE.get() << 2) + 4L * code);
            } else {
                pc.set(stvec.read() & ~3L);  // BASE field, clear MODE bits
            }

            // scause = interrupt bit | exception code
            scause.INTERRUPT.set(cause.getInterrupt());
            scause.EXCEPTION_CODE.set(code);

            // stval = faulting info
            writeTrapValue(hartData, cause, stval);

            // mstatus.SPIE = mstatus.SIE
            mstatus.SPIE.set(mstatus.SIE.get());
            // mstatus.SIE = 0
            mstatus.SIE.set(0);
            // mstatus.SPP = current privilege mode (0=User, 1=Supervisor)
            mstatus.SPP.set(currentPriv == PRIVILEGE_MODE.SUPERVISOR ? 1 : 0);

            // Enter supervisor mode
            hartData.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);

        } else {
            // ---- Trap to M-mode (original behavior) ----
            MTVEC_CSR mtvec = (MTVEC_CSR) MMCSR.getCSR(hartData, MMCSR.MTVEC);
            MCAUSE_CSR mcause = (MCAUSE_CSR) MMCSR.getCSR(hartData, MMCSR.MCAUSE);
            CSR mtval = MMCSR.getCSR(hartData, MMCSR.MTVAL);

            // mepc = PC
            hartData.setCSR(MMCSR.MEPC.getAddress(), pc.get());

            // PC = mtvec (vectored or direct)
            if (isInterrupt && (mtvec.MODE.get() == 1)) {
                pc.set((mtvec.BASE.get() << 2) + 4L * code);
            } else {
                pc.set(MMCSR.getValue(hartData, MMCSR.MTVEC) & ~3L);
            }

            // mcause = interrupt bit | exception code
            mcause.INTERRUPT.set(cause.getInterrupt());
            mcause.EXCEPTION_CODE.set(code);

            // mtval = faulting info
            writeTrapValue(hartData, cause, mtval);

            // mstatus.MPIE = mstatus.MIE
            mstatus.MPIE.set(mstatus.MIE.get());
            // mstatus.MIE = 0
            mstatus.MIE.set(0);
            // mstatus.MPP = current privilege mode
            mstatus.MPP.set(currentPriv.getValue());

            // Enter machine mode
            hartData.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
        }
    }

    /**
     * Write the trap value (mtval/stval) based on the cause.
     */
    private static void writeTrapValue(rv32imData hartData, MCAUSE_CSR.TRAP_CAUSE cause, CSR tval) {
        switch (cause) {
            case INSTRUCTION_ACCESS_FAULT:
                tval.write(hartData.getIR().get());
                break;
            case INSTRUCTION_PAGE_FAULT:
            case LOAD_PAGE_FAULT:
            case STORE_PAGE_FAULT:
                tval.write(hartData.getPendingVirtualAddress());
                break;
            default:
                tval.write(0);
        }
    }

    public static void throwIllegalInstructionException(rv32imData hartData) {
        TrapHandler.handle(hartData, MCAUSE_CSR.TRAP_CAUSE.ILLEGAL_INSTRUCTION);
    }
}
