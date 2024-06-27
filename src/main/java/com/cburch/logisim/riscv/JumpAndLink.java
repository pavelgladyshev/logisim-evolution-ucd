package com.cburch.logisim.riscv;

public class JumpAndLink {
    public static void link(rv32imData hartData) {
        InstructionRegister ir = hartData.getIR();
        ProgramCounter pc = new ProgramCounter(hartData.getPC().get());
        pc.increment();
        hartData.setX(ir.rd(), (pc.get() & 0xffffffffL));
    }
}
