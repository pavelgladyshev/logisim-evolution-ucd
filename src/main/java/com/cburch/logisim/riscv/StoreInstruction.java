package com.cburch.logisim.riscv;

import com.cburch.logisim.analyze.model.Var;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

import static com.cburch.logisim.riscv.rv32imData.HiZ32;

public class StoreInstruction {

    public static void latch(rv32imData hartData) {
        hartData.setAddressing(false);
    }

    public static void fetch(rv32imData hartData)
    {
        hartData.setFetching(false);
        hartData.setAddressing(true);

        // Values for outputs fetching data
        hartData.setAddress(StoreInstruction.getAddress(hartData));
        System.out.println(hartData.getAddress());
        hartData.setOutputData(StoreInstruction.getData(hartData));     // The output data bus is data from instruction
        hartData.setOutputDataWidth(4);    // all 4 bytes of the output
        hartData.setMemRead(Value.FALSE);   //  MemRead is not active
        hartData.setMemWrite(Value.TRUE); // MemWrite active
        hartData.setIsSync(Value.TRUE);
    }

    public static Value getData(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        int rs2 = ir.rs2();
        long data = hartData.getX(rs2);
        Value result = Value.createUnknown(BitWidth.create(32));

        switch (ir.func3()) {
            case 0x0:  // sb rs2, imm(rs1)
                result = Value.createKnown(BitWidth.create(32), getDataByte(data, hartData.getAddress().toLongValue()));
                break;
            case 0x1:  // sh rs2, imm(rs1)
                result = Value.createKnown(BitWidth.create(32), getDataHalf(data, hartData.getAddress().toLongValue()));
                break;
            case 0x2:  // sw rs2, imm(rs1)
                result = Value.createKnown(BitWidth.create(32), (data ^ 0x80000000) - 0x80000000);
                break;
            default:
                hartData.halt();
        }
        return result;
    }

    private static long getDataByte(long data, long address) {
        long shift = (address & 0x3) * 8;
        return (data & 0xFF) << shift;
    }

    private static long getDataHalf(long data, long address) {
        long shift = (address & 0x2) * 8;
        return (data & 0xFFFF) << shift;
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
