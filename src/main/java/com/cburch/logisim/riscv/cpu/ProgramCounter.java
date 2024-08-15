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

    public void set(long value) {
        this.value = value;
    }

}
