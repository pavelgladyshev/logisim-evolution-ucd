package com.cburch.logisim.riscv;

public class CSR_RW extends CSR_R {
    CSR_RW(long initValue) {
        super(initValue);
    }

    @Override
    protected void write(long value) {
        this.value = value;
    }
}
