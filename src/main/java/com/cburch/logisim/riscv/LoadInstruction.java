package com.cburch.logisim.riscv;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class LoadInstruction {
    private static int rd;
    private static int func3;

    public static void loadData(rv32imData state) {
        long data = state.getLastDataIn();
        System.out.println(rd + func3);
        switch (func3) {
            case 0x0:  // lb rd, imm(rs1)
                break;
            case 0x1:  // lh rd, imm(rs1)
                break;
            case 0x2:  // lw rd, imm(rs1)
                state.setX(rd, data);
                break;
            case 0x4:  // lbu rd, imm(rs1)
                break;
            case 0x5:  // lhu rd, imm(rs1)
                break;
            default:
                // Exception
        }
    }

    public static Value getAddress(rv32imData state) {
        InstructionRegister ir = state.getIR();
        int rs1 = ir.rs1();
        long imm_I = ir.imm_I();
        long baseAddress = state.getX(rs1);
        long address = baseAddress + imm_I;
        return Value.createKnown(BitWidth.create(32), address);
    }

    public static void setRd(int newRd) { rd = newRd; }
    public static void setfunc3(int newFunc3) { func3 = newFunc3; }
}
