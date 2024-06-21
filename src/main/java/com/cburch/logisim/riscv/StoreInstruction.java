package com.cburch.logisim.riscv;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class StoreInstruction {

    public static Value getData(rv32imData state) {
        InstructionRegister ir = state.getIR();
        int rs2 = ir.rs2();
        long data = state.getX(rs2);
        Value result = Value.createUnknown(BitWidth.create(32));

        switch (ir.func3()) {
            case 0x0:  // sb rs2, imm(rs1)
                break;
            case 0x1:  // sh rs2, imm(rs1)
                break;
            case 0x2:  // sw rs2, imm(rs1)
                result = Value.createKnown(BitWidth.create(32), data);
                System.out.println(result.toHexString());
                break;
            default:
                // Exception
        }
        return result;
    }

    public static Value getAddress(rv32imData state) {
        InstructionRegister ir = state.getIR();
        int rs1 = ir.rs1();
        long imm_S = ir.imm_S();
        long baseAddress = state.getX(rs1);
        long address = baseAddress + imm_S;
        return Value.createKnown(BitWidth.create(32), address);
    }
}
