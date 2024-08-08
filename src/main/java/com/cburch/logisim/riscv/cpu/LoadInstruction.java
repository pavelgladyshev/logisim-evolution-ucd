package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

import static com.cburch.logisim.riscv.cpu.rv32imData.HiZ32;

public class LoadInstruction {

    public static void latch(rv32imData hartData, long data) {
        long address = getAddress(hartData).toLongValue();
        InstructionRegister ir = hartData.getIR();

        switch (ir.func3()) {
            case 0x0:  // lb rd, imm(rs1)
                hartData.setX(ir.rd(), ((getUnsignedDataByte(data, address) ^ 0x80) - 0x80));
                break;
            case 0x1:  // lh rd, imm(rs1)
                hartData.setX(ir.rd(), ((getUnsignedDataHalf(data, address) ^ 0x8000) - 0x8000));
                break;
            case 0x2:  // lw rd, imm(rs1)
                hartData.setX(ir.rd(), (data ^ 0x80000000L) - 0x80000000L);
                break;
            case 0x4:  // lbu rd, imm(rs1)
                hartData.setX(ir.rd(), getUnsignedDataByte(data, address));
                break;
            case 0x5:  // lhu rd, imm(rs1)
                hartData.setX(ir.rd(), getUnsignedDataHalf(data, address));
                break;
            default:
                hartData.halt();
        }
    }

    public static void performAddressing(rv32imData hartData) {
        hartData.setFetching(false);
        hartData.setAddressing(true);

        // Values for outputs fetching data
        hartData.setAddress(LoadInstruction.getAddress(hartData));
        hartData.setOutputData(HiZ32);     // The output data bus is in High Z
        hartData.setOutputDataWidth(4);    // all 4 bytes of the output
        hartData.setMemRead(Value.TRUE);   //  MemRead active
        hartData.setMemWrite(Value.FALSE); // MemWrite not active
        hartData.setIsSync(Value.TRUE);
    }

    private static long getUnsignedDataByte(long data, long address) {
        long shift = address & 3;
        long mask = 0xffL << shift*8;
        return (data & mask) >>> (shift * 8);
    }

    private static long getUnsignedDataHalf(long data, long address) {
        long shift = address & 3;
        long mask = 0xffffL << shift*8;
        return (data & mask) >>> (shift * 8);
    }

    public static Value getAddress(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        return Value.createKnown(BitWidth.create(32), (hartData.getX(ir.rs1()) + ir.imm_I()));
    }
}
