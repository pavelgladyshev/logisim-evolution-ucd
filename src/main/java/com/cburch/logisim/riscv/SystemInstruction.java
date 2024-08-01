package com.cburch.logisim.riscv;

import static com.cburch.logisim.riscv.MMCSR.*;
public class SystemInstruction {
    public static void execute(rv32imData state) {
        int funct3 = state.getIR().func3();
        int csr = state.getIR().csr();
        int rd = state.getIR().rd();
        int rs1 = state.getIR().rs1();
        long imm_I = state.getIR().imm_I();
        long rs1Value = state.getX(rs1);
        long csrValue;
        long result;

        switch (funct3) {
            case 0x0: { // ecall/ebreak/_ret
                MSTATUS_CSR mstatus = ((MSTATUS_CSR)MMCSR.getCSR(state, MSTATUS));
                if (imm_I == 0) {
                    // ecall
                    MSTATUS_CSR.MPP mpp = (MSTATUS_CSR.MPP)mstatus.MPP;
                    switch(mpp.getLastPrivilegeMode()){
                        case USER:
                            TrapHandler.handle(state, MCAUSE_CSR.TRAP_CAUSE.ENVIRONMENT_CALL_FROM_U_MODE);
                            break;
                        case SUPERVISOR:
                            TrapHandler.handle(state, MCAUSE_CSR.TRAP_CAUSE.ENVIRONMENT_CALL_FROM_S_MODE);
                            break;
                        case MACHINE:
                            TrapHandler.handle(state, MCAUSE_CSR.TRAP_CAUSE.ENVIRONMENT_CALL_FROM_M_MODE);
                            break;
                    }
                } else if (imm_I == 1) {
                    //ebreak
                    TrapHandler.handle(state, MCAUSE_CSR.TRAP_CAUSE.BREAKPOINT);
                } else if (rs1 == 0 && rd == 0 && csr == 0x2) {
                    // uret
                    // PC = uepc
                } else if (rs1 == 0 && rd == 0 && csr == 0x102) {
                    // sret
                    // PC = sepc
                } else if (rs1 == 0 && rd == 0 && csr == 0x302) {
                    // mret
                    state.getPC().set(MMCSR.getValue(state, MEPC) );
                    mstatus.MIE.set(mstatus.MPIE.get());
                    mstatus.MPIE.set(1);
                    CSR mip =  MMCSR.getCSR(state, MIP);
                    mip.write(mip.read() & (~0x80));
                    // set MPP to user mode if implemented
                    mstatus.MPP.set(PRIVILEGE_MODE.MACHINE.getValue());
                }
                break;
            }
            case 0x1: // csrrw rd,csr,rs1
                csrValue = state.getCSRValue(csr);
                result = rs1Value;
                state.setCSR(csr, result);
                state.setX(rd, csrValue);
                break;
            case 0x2: // csrrs rd,csr,rs1
                csrValue = state.getCSRValue(csr);
                result = csrValue | rs1Value;
                state.setCSR(csr, result);
                state.setX(rd, csrValue);
                break;
            case 0x3: // csrrc rd,csr,rs1
                csrValue = state.getCSRValue(csr);
                result = csrValue & ~rs1Value;
                state.setCSR(csr, result);
                state.setX(rd, csrValue);
                break;
            case 0x5: // csrrwi rd,csr,uimm
                csrValue = state.getCSRValue(csr);
                result = state.getIR().rs1();
                state.setCSR(csr, result);
                state.setX(rd, csrValue);
                break;
            case 0x6: // csrrsi rd,csr,uimm
                csrValue = state.getCSRValue(csr);
                result = csrValue | state.getIR().rs1();
                state.setCSR(csr, result);
                state.setX(rd, csrValue);
                break;
            case 0x7: // csrrci rd,csr,uimm
                csrValue = state.getCSRValue(csr);
                result = csrValue & ~state.getIR().rs1();
                state.setCSR(csr, result);
                state.setX(rd, csrValue);
                break;
            default:
                state.halt();
                break;
        }
    }

}
