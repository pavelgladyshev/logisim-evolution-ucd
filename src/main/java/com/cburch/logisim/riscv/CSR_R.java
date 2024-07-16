package com.cburch.logisim.riscv;

public class CSR_R extends CSR {
    CSR_R(long initValue) {
        value = initValue;
    }

    protected long value;
    protected long read() {
        return value;
    }
}
