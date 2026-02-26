package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.csrs.*;
import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.*;

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
            case 0x0: { // ecall/ebreak/_ret
                MSTATUS_CSR mstatus = ((MSTATUS_CSR) MMCSR.getCSR(hartData, MSTATUS));
                if (imm_I == 0) {
                    // ecall
                    MSTATUS_CSR.MPP mpp = (MSTATUS_CSR.MPP)mstatus.MPP;
                    switch(mpp.getLastPrivilegeMode()){
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
                } else if (imm_I == 1) {
                    //ebreak
                    TrapHandler.handle(hartData, MCAUSE_CSR.TRAP_CAUSE.BREAKPOINT);
                } else if (rs1 == 0 && rd == 0 && csr == 0x2) {
                    // uret
                    // PC = uepc
                } else if (rs1 == 0 && rd == 0 && csr == 0x102) {
                    // sret
                    // PC = sepc
                } else if (ir.func7() == 0x09) {
                    // sfence.vma rs1, rs2
                    TranslationLookasideBuffer tlb = hartData.getTlb();
                    int rs2Val = ir.rs2();
                    if (rs1 == 0 && rs2Val == 0) {
                        tlb.invalidate();
                    } else if (rs1 != 0 && rs2Val == 0) {
                        tlb.invalidateAddress(hartData.getX(rs1));
                    } else if (rs1 == 0) {
                        tlb.invalidateASID((int) hartData.getX(rs2Val));
                    } else {
                        tlb.invalidateAddressAndASID(hartData.getX(rs1), (int) hartData.getX(rs2Val));
                    }
                    hartData.getInstructionCache().invalidate();
                    hartData.getPC().increment();
                } else if (rs1 == 0 && rd == 0 && csr == 0x302) {
                    // mret
                    hartData.getPC().set(MMCSR.getValue(hartData, MEPC) );
                    mstatus.MIE.set(mstatus.MPIE.get());
                    mstatus.MPIE.set(1);
                    CSR mip =  MMCSR.getCSR(hartData, MIP);
                    mip.write(mip.read() & (~0x80));
                    mip.write(mip.read() & (~0x800));
                    // set MPP to user mode if implemented
                    mstatus.MPP.set(PRIVILEGE_MODE.MACHINE.getValue());
                } else {
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
