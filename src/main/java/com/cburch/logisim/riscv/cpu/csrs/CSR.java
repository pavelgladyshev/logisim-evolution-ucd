package com.cburch.logisim.riscv.cpu.csrs;

public abstract class CSR {
    abstract public long read();
    public void write(long value) {
    };
}
