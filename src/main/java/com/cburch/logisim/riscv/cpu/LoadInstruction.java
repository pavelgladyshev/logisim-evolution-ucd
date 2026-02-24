package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class LoadInstruction {

    public static void latch(rv32imData hartData, long data) {
        long address = getAddress(hartData);
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
        }
        hartData.getPC().increment();
    }

    public static void performAddressing(rv32imData hartData) {

        //never reach latch step if instruction is invalid
        InstructionRegister ir = hartData.getIR();
        if(!(ir.func3() == 0x0 || ir.func3() == 0x1 || ir.func3() == 0x2 || ir.func3() == 0x4 || ir.func3() == 0x5)) {
            TrapHandler.throwIllegalInstructionException(hartData);
            return;
        }

        long address = LoadInstruction.getAddress(hartData);
        int width = 0;
        switch (ir.func3()) {
            case 0x0:  // lh rd, imm(rs1)
            case 0x4: // lbu (unsigned byte)
                width = 1;
                break;
            case 0x1: // lh (halfword)
            case 0x5:  // lhu rd, imm(rs1)
                width = 2;
                if ((address & 0x1) != 0) {
                    TrapHandler.throwIllegalInstructionException(hartData);
                    return;
                }
                break;
            case 0x2:  // lw rd, imm(rs1)
                width = 4;
                if ((address & 0x3) != 0) {
                    TrapHandler.throwIllegalInstructionException(hartData);
                    return;
                }
                break;
        }

        hartData.setFetching(false);
        hartData.setAddressing(true);

        // Values for outputs fetching data
        hartData.setAddress(Value.createKnown(BitWidth.create(32), getAddress(hartData)));
        hartData.setOutputData(0);
        hartData.setOutputDataWidth(width);    // The output data bus is in High Z for all 4 bytes of the output
        hartData.setMemRead(Value.TRUE);   //  MemRead active
        hartData.setMemWrite(Value.FALSE); // MemWrite not active
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

    /**
     * Like performAddressing but uses a pre-translated physical address instead of
     * computing the virtual address from registers. Used when SV32 translation is active.
     */
    public static void performAddressingWithPA(rv32imData hartData, long physicalAddress) {
        InstructionRegister ir = hartData.getIR();
        if(!(ir.func3() == 0x0 || ir.func3() == 0x1 || ir.func3() == 0x2 || ir.func3() == 0x4 || ir.func3() == 0x5)) {
            TrapHandler.throwIllegalInstructionException(hartData);
            return;
        }

        // Alignment checks use the virtual address (same low bits as physical for aligned pages)
        int width = 0;
        switch (ir.func3()) {
            case 0x0:
            case 0x4:
                width = 1;
                break;
            case 0x1:
            case 0x5:
                width = 2;
                if ((physicalAddress & 0x1) != 0) {
                    TrapHandler.throwIllegalInstructionException(hartData);
                    return;
                }
                break;
            case 0x2:
                width = 4;
                if ((physicalAddress & 0x3) != 0) {
                    TrapHandler.throwIllegalInstructionException(hartData);
                    return;
                }
                break;
        }

        hartData.setFetching(false);
        hartData.setAddressing(true);

        hartData.setAddress(Value.createKnown(32, physicalAddress));
        hartData.setOutputData(0);
        hartData.setOutputDataWidth(width);
        hartData.setMemRead(Value.TRUE);
        hartData.setMemWrite(Value.FALSE);
    }

    public static long getAddress(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        return hartData.getX(ir.rs1()) + ir.imm_I();
    }
}
