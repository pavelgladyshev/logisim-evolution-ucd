package com.cburch.logisim.riscv.cpu;

public class ProgramCounter {
    long value;

    public ProgramCounter(long value) {
        set(value);
    }

    public void increment() {
        value = (value + 4) & 0xffffffffL;
    }

    public long get() {
        return value;
    }

    /** The pc holds 32 bits (a branch past 0 or 2^32, or an mepc from a sign-extended register, wraps). */
    public void set(long value) {
        this.value = value & 0xffffffffL;
    }

}
