package com.cburch.logisim.riscv;

public class JumpAndLink {
    public static void link(rv32imData hartData){
        InstructionRegister ir = hartData.getIR();
        ProgramCounter pc = new ProgramCounter(hartData.getPC().value);
        pc.increment();
        hartData.setX(ir.rd(), (pc.value & 0xffffffffL));
    }
}
