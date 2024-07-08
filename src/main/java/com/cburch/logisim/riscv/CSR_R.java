package com.cburch.logisim.riscv;

public class CSR_R extends CSR {
    protected long value;

    protected long read() {
        return value;
    }

    CSR_R(long initValue) {
        value = initValue;
    }
}
