package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.csrs.*;
import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.*;
import com.cburch.logisim.riscv.cpu.csrs.SCSR;

public class SystemInstruction {
    public static void execute(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        int funct3 = ir.func3();
        int csr = ir.csr();
        int rd = ir.rd();
        int rs1 = ir.rs1();
        long imm_I = ir.imm_I();
        long rs1Value = hartData.getX(rs1);
        long csrValue;
        boolean illegalInstructionExceptionTriggered = false;

        // For Zicsr instructions (funct3 1-7), perform upfront access validation
        // before any CSR read or write side effects, per RISC-V spec.
        boolean willWrite = false;
        if (funct3 >= 0x1 && funct3 <= 0x7) {
            // Determine if this instruction will actually write the CSR
            willWrite = switch (funct3) {
                case 0x1, 0x5 -> true;           // CSRRW/CSRRWI always write
                case 0x2, 0x3 -> rs1 != 0;       // CSRRS/CSRRC write only if rs1 != x0
                case 0x6, 0x7 -> ir.rs1() != 0;  // CSRRSI/CSRRCI write only if uimm != 0
                default -> true;
            };

            // Check privilege level and write permission upfront
            if (!hartData.getCSRFile().checkAccess(hartData, csr, willWrite)) {
                // Exception already raised by checkAccess — do not proceed
                return;
            }
        }

        switch (funct3) {
            case 0x0: { // ecall/ebreak/_ret, wfi and sfence.vma: each with its whole encoding
                MSTATUS_CSR mstatus = ((MSTATUS_CSR) MMCSR.getCSR(hartData, MSTATUS));
                final long instruction = ir.get() & 0xFFFFFFFFL;
                final PRIVILEGE_MODE mode = hartData.getCurrentPrivilegeMode();
                if (instruction == 0x00000073L) {
                    // ecall — use actual current privilege mode, not MSTATUS.MPP
                    switch(mode){
                        case USER:
                            TrapHandler.handle(hartData, MCAUSE_CSR.TRAP_CAUSE.ENVIRONMENT_CALL_FROM_U_MODE);
                            break;
                        case SUPERVISOR:
                            TrapHandler.handle(hartData, MCAUSE_CSR.TRAP_CAUSE.ENVIRONMENT_CALL_FROM_S_MODE);
                            break;
                        case MACHINE:
                            TrapHandler.handle(hartData, MCAUSE_CSR.TRAP_CAUSE.ENVIRONMENT_CALL_FROM_M_MODE);
                            break;
                    }
                } else if (instruction == 0x00100073L) {
                    //ebreak
                    TrapHandler.handle(hartData, MCAUSE_CSR.TRAP_CAUSE.BREAKPOINT);
                } else if (instruction == 0x10200073L && mode != PRIVILEGE_MODE.USER) {
                    // sret — return from S-mode trap (in S- or M-mode; there is no mstatus.TSR)
                    hartData.getPC().set(SCSR.getValue(hartData, SCSR.SEPC));
                    // Restore SIE from SPIE
                    mstatus.SIE.set(mstatus.SPIE.get());
                    mstatus.SPIE.set(1);
                    // Restore privilege mode from SPP (0=User, 1=Supervisor)
                    PRIVILEGE_MODE prevMode = mstatus.SPP.getLastPrivilegeMode();
                    hartData.setCurrentPrivilegeMode(prevMode);
                    // Set SPP to User (least privileged)
                    mstatus.SPP.set(0);
                    // as mret: MPRV cleared when the new mode is not M
                    mstatus.MPRV.set(0);
                } else if (instruction == 0x30200073L && mode == PRIVILEGE_MODE.MACHINE) {
                    // mret — return from M-mode trap (in M-mode only)
                    hartData.getPC().set(MMCSR.getValue(hartData, MEPC));
                    // Restore MIE from MPIE
                    mstatus.MIE.set(mstatus.MPIE.get());
                    mstatus.MPIE.set(1);
                    // Restore privilege mode from MPP
                    MSTATUS_CSR.MPP mpp = (MSTATUS_CSR.MPP) mstatus.MPP;
                    PRIVILEGE_MODE restoredMode = mpp.getLastPrivilegeMode();
                    hartData.setCurrentPrivilegeMode(restoredMode);
                    // Set MPP to least privileged mode (User)
                    mstatus.MPP.set(PRIVILEGE_MODE.USER.getValue());
                    // Per spec §3.1.6.3: clear MPRV if new privilege != Machine
                    if (restoredMode != PRIVILEGE_MODE.MACHINE) {
                        mstatus.MPRV.set(0);
                    }
                } else if (instruction == 0x10500073L) {
                    // wfi — may go on at once (an interrupt is what it waits for, and the CPU takes it next)
                    hartData.getPC().increment();
                } else if ((instruction & 0xFE007FFFL) == 0x12000073L && mode != PRIVILEGE_MODE.USER) {
                    // sfence.vma rs1, rs2 (in S- or M-mode; there is no mstatus.TVM): rs2's ASID is its 9 low bits
                    TranslationLookasideBuffer tlb = hartData.getTlb();
                    int rs2Val = ir.rs2();
                    final int asid = (int) (hartData.getX(rs2Val) & 0x1FF);
                    if (rs1 == 0 && rs2Val == 0) {
                        tlb.invalidate();
                    } else if (rs1 != 0 && rs2Val == 0) {
                        tlb.invalidateAddress(hartData.getX(rs1));
                    } else if (rs1 == 0) {
                        tlb.invalidateASID(asid);
                    } else {
                        tlb.invalidateAddressAndASID(hartData.getX(rs1), asid);
                    }
                    hartData.getInstructionCache().invalidate();
                    hartData.getPC().increment();
                } else {
                    // any other, uret among them (no N extension), and mret, sret or sfence.vma in too low a mode
                    illegalInstructionExceptionTriggered = true;
                }
                break;
            }
            case 0x1: // csrrw rd,csr,rs1
                csrValue = hartData.getCSRValue(csr);
                hartData.setX(rd, csrValue);
                hartData.setCSR(csr, rs1Value);
                break;
            case 0x2: // csrrs rd,csr,rs1
                csrValue = hartData.getCSRValue(csr);
                hartData.setX(rd, csrValue);
                if (willWrite) hartData.setCSR(csr, csrValue | rs1Value);
                break;
            case 0x3: // csrrc rd,csr,rs1
                csrValue = hartData.getCSRValue(csr);
                hartData.setX(rd, csrValue);
                if (willWrite) hartData.setCSR(csr, csrValue & ~rs1Value);
                break;
            case 0x5: // csrrwi rd,csr,uimm
                csrValue = hartData.getCSRValue(csr);
                hartData.setX(rd, csrValue);
                hartData.setCSR(csr, ir.rs1());
                break;
            case 0x6: // csrrsi rd,csr,uimm
                csrValue = hartData.getCSRValue(csr);
                hartData.setX(rd, csrValue);
                if (willWrite) hartData.setCSR(csr, csrValue | ir.rs1());
                break;
            case 0x7: // csrrci rd,csr,uimm
                csrValue = hartData.getCSRValue(csr);
                hartData.setX(rd, csrValue);
                if (willWrite) hartData.setCSR(csr, csrValue & ~ir.rs1());
                break;
            default:
                illegalInstructionExceptionTriggered = true;
        }
        if(illegalInstructionExceptionTriggered) TrapHandler.throwIllegalInstructionException(hartData);
        else if(funct3 != 0x0) hartData.getPC().increment();
    }
}
