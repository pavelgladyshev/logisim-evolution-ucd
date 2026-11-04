package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class StoreInstruction {

    /** Whether the funct3 is a store's: sb, sh or sw. */
    public static boolean isValid(rv32imData hartData) {
        final int func3 = hartData.getIR().func3();
        return func3 == 0x0 || func3 == 0x1 || func3 == 0x2;
    }

    /** The bytes a (valid) store writes: 1, 2 or 4. */
    public static int getWidth(rv32imData hartData) {
        return switch (hartData.getIR().func3()) {
            case 0x1 -> 2;
            case 0x2 -> 4;
            default -> 1;
        };
    }

    /** The bus cycle of a valid and aligned store, without translation (rv32imData checks both first). */
    public static void performAddressing(rv32imData hartData) {
        long address = getAddress(hartData);
        int dataWidth = getWidth(hartData);
        hartData.setFetching(false);
        hartData.setAddressing(true);
        hartData.setOutputData(getNewData(hartData));
        hartData.setOutputDataWidth(dataWidth);

        // Values for outputs fetching data
        hartData.setAddress(Value.createKnown(32, address));
        hartData.setMemRead(Value.FALSE);
        hartData.setMemWrite(Value.TRUE);
    }

    private static long getNewData(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        return hartData.getX(ir.rs2());
    }


    /**
     * Like performAddressing but uses a pre-translated physical address instead of
     * computing the virtual address from registers. Used when SV32 translation is active.
     */
    public static void performAddressingWithPA(rv32imData hartData, long physicalAddress) {
        int dataWidth = getWidth(hartData);
        hartData.setFetching(false);
        hartData.setAddressing(true);
        hartData.setOutputData(getNewData(hartData));
        hartData.setOutputDataWidth(dataWidth);

        hartData.setAddress(Value.createKnown(32, physicalAddress));
        hartData.setMemRead(Value.FALSE);
        hartData.setMemWrite(Value.TRUE);
    }

    public static long getAddress(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        return hartData.getX(ir.rs1()) + ir.imm_S();
    }

}
