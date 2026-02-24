package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class StoreInstruction {

    public static void performAddressing(rv32imData hartData) {

        InstructionRegister ir = hartData.getIR();
        if(!(ir.func3() == 0x0 || ir.func3() == 0x1 || ir.func3() == 0x2)) {
            TrapHandler.throwIllegalInstructionException(hartData);
            return;
        }

        long address = getAddress(hartData);
        int dataWidth = 0;

        switch (ir.func3()) {
            case 0x0:  // sb rs2, imm(rs1)
                dataWidth = 1;
                break;
            case 0x1:  // sh rs2, imm(rs1)
                if ((address & 0x1) != 0) {
                    TrapHandler.throwIllegalInstructionException(hartData);
                    return;
                } else {
                    dataWidth = 2;
                }
                break;
            case 0x2:  // sw rs2, imm(rs1)
                if ((address & 0x3) != 0) {
                    TrapHandler.throwIllegalInstructionException(hartData);
                    return;
                } else {
                    dataWidth = 4;
                }
                break;
        }

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
        InstructionRegister ir = hartData.getIR();
        if(!(ir.func3() == 0x0 || ir.func3() == 0x1 || ir.func3() == 0x2)) {
            TrapHandler.throwIllegalInstructionException(hartData);
            return;
        }

        int dataWidth = 0;
        switch (ir.func3()) {
            case 0x0:
                dataWidth = 1;
                break;
            case 0x1:
                if ((physicalAddress & 0x1) != 0) {
                    TrapHandler.throwIllegalInstructionException(hartData);
                    return;
                } else {
                    dataWidth = 2;
                }
                break;
            case 0x2:
                if ((physicalAddress & 0x3) != 0) {
                    TrapHandler.throwIllegalInstructionException(hartData);
                    return;
                } else {
                    dataWidth = 4;
                }
                break;
        }

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
