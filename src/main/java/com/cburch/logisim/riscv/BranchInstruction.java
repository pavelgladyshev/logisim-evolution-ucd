package com.cburch.logisim.riscv;

public class BranchInstruction {

    public static void execute(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        boolean takeBranch = false;

        switch (ir.func3()) {
            case 0x0:   // beq rs1, rs2, label
                if (hartData.getX(ir.rs1()) == hartData.getX(ir.rs2())) {
                    takeBranch = true;
                }
                break;
            case 0x1:   // bne rs1, rs2, label
                if (hartData.getX(ir.rs1()) != hartData.getX(ir.rs2())) {
                    takeBranch = true;
                }
                break;
            case 0x4:   // blt rs1, rs2, label
                if (hartData.getX(ir.rs1()) < hartData.getX(ir.rs2())) {
                    takeBranch = true;
                }
                break;
            case 0x5:   // bge rs1, rs2, label
                if (hartData.getX(ir.rs1()) >= hartData.getX(ir.rs2())) {
                    takeBranch = true;
                }
                break;
            case 0x6:   // bltu rs1, rs2, label
                if (Long.compareUnsigned(hartData.getX(ir.rs1()), hartData.getX(ir.rs2())) < 0) {
                    takeBranch = true;
                }
                break;
            case 0x7:   // bgeu rs1, rs2, label
                if (Long.compareUnsigned(hartData.getX(ir.rs1()), hartData.getX(ir.rs2())) >= 0) {
                    takeBranch = true;
                }
                break;
        }

        if (takeBranch) {
            hartData.getPC().set(hartData.getPC().get() + ir.imm_B());
        } else {
            hartData.getPC().increment();
        }
    }
}
