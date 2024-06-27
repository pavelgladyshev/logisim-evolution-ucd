package com.cburch.logisim.riscv;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class StoreInstruction {

    static long newData;
    static long address;

    public static void latch(rv32imData hartData, long data) {
        System.out.println("X" + newData + " " + data);
        StoreInstruction.storeIntermixedData(hartData, data);
        hartData.setOutputDataWidth(4);
        hartData.setMemRead(Value.TRUE);
        hartData.setMemWrite(Value.TRUE);
        hartData.setIsSync(Value.TRUE);
    }

    public static void performAddressing(rv32imData hartData) {
        hartData.setFetching(false);
        hartData.setAddressing(true);

        newData = getNewData(hartData).toLongValue();
        address = getAddress(hartData).toLongValue();

        // Values for outputs fetching data
        hartData.setAddress(Value.createKnown(32, address - (address % 4) ) );

        // Check for correct offset
        if (hartData.getOutputDataWidth() == 4) {
            if (address % 4 != 1 && address % 4 != 3) {
                hartData.halt();
                hartData.setMemWrite(Value.FALSE);
                return;
            }
        }

        if (hartData.getOutputDataWidth() == 8) {
            if (address % 4 != 2) {
                hartData.halt();
                hartData.setMemWrite(Value.FALSE);
                return;
            }
        }

        hartData.setMemRead(Value.TRUE);
        hartData.setMemWrite(Value.TRUE);
        hartData.setIsSync(Value.TRUE);

    }

    public static Value getNewData(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        int rs2 = ir.rs2();
        long data = hartData.getX(rs2);
        Value result = Value.createKnown(32, 0);

        switch (ir.func3()) {
            case 0x0:  // sb rs2, imm(rs1)
                hartData.setOutputDataWidth(1);
                result = Value.createKnown(BitWidth.create(32),
                        data << (hartData.getAddress().toLongValue() & 0x3) * 8);
                break;
            case 0x1:  // sh rs2, imm(rs1)
                hartData.setOutputDataWidth(4);
                result = Value.createKnown(BitWidth.create(32),
                        data << (hartData.getAddress().toLongValue() & 0x3) * 8);
                break;
            case 0x2:  // sw rs2, imm(rs1)
                hartData.setOutputDataWidth(8);
                result = Value.createKnown(BitWidth.create(32), (data ^ 0x80000000L) - 0x80000000L);
                break;
            default:
                hartData.halt();
        }

        return result;
    }

    public static void storeIntermixedData(rv32imData hartData, long data) {
        switch (hartData.getOutputDataWidth()) {
            case 1:
                if (address % 4 == 0) {
                    data -= (data & 0xFF);
                    hartData.setOutputData(Value.createKnown(32, data | newData));
                } else if (address % 4 == 1) {
                    data -= (data & 0xFF00);
                    hartData.setOutputData(Value.createKnown(32, data | (newData << 8) ) );
                } else if (address % 4 == 2) {
                    data -= (data & 0xFF0000);
                    hartData.setOutputData(Value.createKnown(32, data | (newData << 16) ) );
                } else if (address % 4 == 3) {
                    data -= (data & 0xFF000000L);
                    hartData.setOutputData(Value.createKnown(32, data | (newData << 24) ) );
                }
                break;

            case 4:
                if (address % 4 == 1) {
                    data -= (data & 0xFFFF);
                    hartData.setOutputData(Value.createKnown(32, data | newData));
                } else if (address % 4 == 3) {
                    data -= (data & 0xFFFF0000L);
                    hartData.setOutputData(Value.createKnown(32, data | (newData << 16) ) );
                }
                break;

            case 8:
                hartData.setOutputData(Value.createKnown(32, newData));
                break;
        }
    }

    public static Value getAddress(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        int rs1 = ir.rs1();
        long imm_S = ir.imm_S();
        long baseAddress = hartData.getX(rs1);
        long address = baseAddress + imm_S;
        return Value.createKnown(BitWidth.create(32), address);
    }

}
