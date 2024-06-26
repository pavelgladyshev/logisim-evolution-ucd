package com.cburch.logisim.riscv;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class StoreInstruction {

    static long newData;
    static long address;
    static Mode mode;

    enum Mode {
        BYTE,
        HALF,
        WORD
    }

    public static void latch(rv32imData hartData, long data) {
        StoreInstruction.storeData(hartData, data);
        hartData.setOutputDataWidth(4);
        hartData.setMemRead(Value.FALSE);
        hartData.setMemWrite(Value.TRUE);
        hartData.setIsSync(Value.TRUE);
    }

    public static void fetch(rv32imData hartData) {
        hartData.setFetching(false);
        hartData.setAddressing(true);

        newData = getData(hartData).toLongValue();
        address = getAddress(hartData).toLongValue();

        // Values for outputs fetching data
        hartData.setAddress(Value.createKnown(32, address - (address % 4) ) );

        // Check for correct offset
        if (mode == Mode.HALF) {
            if (address % 4 != 1 && address % 4 != 3) {
                hartData.halt();
            }
        }

        if (mode == Mode.WORD) {
            if (address % 4 != 2) {
                hartData.halt();
            }
        }

    }

    public static Value getData(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        int rs2 = ir.rs2();
        long data = hartData.getX(rs2);
        Value result = Value.createUnknown(BitWidth.create(32));

        switch (ir.func3()) {
            case 0x0:  // sb rs2, imm(rs1)
                mode = Mode.BYTE;
                result = Value.createKnown(BitWidth.create(32),
                        data << (hartData.getAddress().toLongValue() & 0x3) * 8);
                break;
            case 0x1:  // sh rs2, imm(rs1)
                mode = Mode.HALF;
                result = Value.createKnown(BitWidth.create(32),
                        data << (hartData.getAddress().toLongValue() & 0x3) * 8);
                break;
            case 0x2:  // sw rs2, imm(rs1)
                mode = Mode.WORD;
                result = Value.createKnown(BitWidth.create(32), (data ^ 0x80000000L) - 0x80000000L);
                break;
            default:
                hartData.halt();
        }
        return result;
    }

    public static void storeData(rv32imData hartData, long data) {
        switch (mode) {
            case BYTE:
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

            case HALF:
                if (address % 4 == 1) {
                    data -= (data & 0xFFFF);
                    hartData.setOutputData(Value.createKnown(32, data | newData));
                } else if (address % 4 == 3) {
                    data -= (data & 0xFFFF0000L);
                    hartData.setOutputData(Value.createKnown(32, data | (newData << 16) ) );
                }
                break;

            case WORD:
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
