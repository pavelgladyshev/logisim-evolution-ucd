package com.cburch.logisim.riscv.cpu.gdb;




public class Breakpoint {
    public enum Type {
        SOFTWARE,
        HARDWARE
    }

    private Type type;
    private long address;
    private int kind;

    public Breakpoint(Type type, long address, int kind) {
        this.type = type;
        this.address = address;
        this.kind = kind;
    }

    public Type getType() {
        return type;
    }

    public long getAddress() {
        return address;
    }

    public int getKind() {
        return kind;
    }

}