package com.cburch.logisim.riscv;

public abstract class CSR {
    abstract protected long read();
    protected void write(long value) {

    };
}
