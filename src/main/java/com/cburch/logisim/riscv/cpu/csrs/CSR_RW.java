package com.cburch.logisim.riscv.cpu.csrs;

public class CSR_RW extends CSR_R {
    public CSR_RW(long initValue) {
        super(initValue);
    }

    @Override
    public void write(long value) {
        this.value = value;
    }
}
