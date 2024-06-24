package com.cburch.logisim.riscv;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class LoadInstruction {
    public static void latch(rv32imData hartData) {
        long data = hartData.getLastDataIn();
        InstructionRegister ir = hartData.getIR();
        switch (ir.func3()) {
            case 0x0:  // lb rd, imm(rs1)
                break;
            case 0x1:  // lh rd, imm(rs1)
                break;
            case 0x2:  // lw rd, imm(rs1)
                hartData.setX(ir.rd(), data);
                break;
            case 0x4:  // lbu rd, imm(rs1)
                break;
            case 0x5:  // lhu rd, imm(rs1)
                break;
            default:
                // Exception
        }
    }

    public static Value getAddress(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        return Value.createKnown(BitWidth.create(32), (hartData.getX(ir.rs1()) + ir.imm_I()));
    }
}
