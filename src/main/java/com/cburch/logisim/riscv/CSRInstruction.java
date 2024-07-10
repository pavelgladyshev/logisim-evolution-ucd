package com.cburch.logisim.riscv;

public class CSRInstruction {
    public static void execute(rv32imData state) {
        int funct3 = state.getIR().func3();
        int csr = state.getIR().csr();
        int rd = state.getIR().rd();
        int rs1 = state.getIR().rs1();
        long rs1Value = state.getX(rs1);
        long csrValue;
        long result;

        switch (funct3) {
            case 0x1: // csrrw rd,csr,rs1
                csrValue = state.getCSR(csr);
                result = rs1Value;
                state.setCSR(csr, result);
                state.setX(rd, csrValue);
                break;
            case 0x2: // csrrs rd,csr,rs1
                csrValue = state.getCSR(csr);
                result = csrValue | rs1Value;
                state.setCSR(csr, result);
                state.setX(rd, csrValue);
                break;
            case 0x3: // csrrc rd,csr,rs1
                csrValue = state.getCSR(csr);
                result = csrValue & ~rs1Value;
                state.setCSR(csr, result);
                state.setX(rd, csrValue);
                break;
            case 0x5: // csrrwi rd,csr,uimm
                csrValue = state.getCSR(csr);
                result = state.getIR().rs1();
                state.setCSR(csr, result);
                state.setX(rd, csrValue);
                break;
            case 0x6: // csrrsi rd,csr,uimm
                csrValue = state.getCSR(csr);
                result = csrValue | state.getIR().rs1();
                state.setCSR(csr, result);
                state.setX(rd, csrValue);
                break;
            case 0x7: // csrrci rd,csr,uimm
                csrValue = state.getCSR(csr);
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
