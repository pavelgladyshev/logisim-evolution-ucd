package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class StoreInstruction {

    public static void performAddressing(rv32imData hartData) {
        hartData.setFetching(false);
        hartData.setAddressing(true);

        hartData.setLastDataIn(getNewData(hartData).toLongValue());
        hartData.setLastAddress(getAddress(hartData).toLongValue());

        // Values for outputs fetching data
        hartData.setAddress(Value.createKnown(
                32, hartData.getLastAddress() - get2LSB(hartData) ) );

        // Check for correct offset
        if (hartData.getOutputDataWidth() == 2) {
            if (get2LSB(hartData) != 0 && get2LSB(hartData) != 2) {
                hartData.halt();
                hartData.setMemWrite(Value.FALSE);
                return;
            }
        }

        if (hartData.getOutputDataWidth() == 4) {
            if (get2LSB(hartData) != 0) {
                hartData.halt();
                hartData.setMemWrite(Value.FALSE);
                return;
            }
        }

        hartData.setMemRead(Value.TRUE);
        hartData.setMemWrite(Value.TRUE);
        hartData.setIsSync(Value.TRUE);

    }

    public static void storeIntermixedData(rv32imData hartData, long data) {
        switch (hartData.getOutputDataWidth()) {
            case 1:
                hartData.setLastDataIn(hartData.getLastDataIn() & 0xFF);
                if (get2LSB(hartData) == 0) {
                    data -= (data & 0xFF);
                    hartData.setOutputData(Value.createKnown(32, data | hartData.getLastDataIn()));
                } else if (get2LSB(hartData) == 1) {
                    data -= (data & 0xFF00);
                    hartData.setOutputData(Value.createKnown(32, data | (hartData.getLastDataIn() << 8) ) );
                } else if (get2LSB(hartData) == 2) {
                    data -= (data & 0xFF0000);
                    hartData.setOutputData(Value.createKnown(32, data | (hartData.getLastDataIn() << 16) ) );
                } else if (get2LSB(hartData) == 3) {
                    data -= (data & 0xFF000000L);
                    hartData.setOutputData(Value.createKnown(32, data | (hartData.getLastDataIn() << 24) ) );
                }
                break;

            case 2:
                hartData.setLastDataIn(hartData.getLastDataIn() & 0xFFFF);
                if (get2LSB(hartData) == 0) {
                    data -= (data & 0xFFFF);
                    hartData.setOutputData(Value.createKnown(32, data | hartData.getLastDataIn()));
                } else if (get2LSB(hartData) == 2) {
                    data -= (data & 0xFFFF0000L);
                    hartData.setOutputData(Value.createKnown(32, data | (hartData.getLastDataIn() << 16) ) );
                }
                break;

            case 4:
                hartData.setOutputData(Value.createKnown(32, hartData.getLastDataIn()));
                break;
        }
    }

    private static Value getNewData(rv32imData hartData) {
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
                hartData.setOutputDataWidth(2);
                result = Value.createKnown(BitWidth.create(32),
                        data << (hartData.getAddress().toLongValue() & 0x3) * 8);
                break;
            case 0x2:  // sw rs2, imm(rs1)
                hartData.setOutputDataWidth(4);
                result = Value.createKnown(BitWidth.create(32), (data ^ 0x80000000L) - 0x80000000L);
                break;
            default:
                hartData.halt();
        }

        return result;
    }

    private static Value getAddress(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        int rs1 = ir.rs1();
        long imm_S = ir.imm_S();
        long baseAddress = hartData.getX(rs1);
        long address = baseAddress + imm_S;
        return Value.createKnown(BitWidth.create(32), address);
    }

    private static long get2LSB(rv32imData hartData) {
        return (hartData.getLastAddress() & 0x3);
    }

}
