package com.cburch.logisim.riscv.cpu.csrs;

public class CSR_R extends CSR {
    public CSR_R(long initValue) {
        value = initValue;
    }

    protected long value;
    public long read() {
        return value;
    }

}
