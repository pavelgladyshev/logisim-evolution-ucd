package com.cburch.logisim.riscv;

public class MEPC extends CSR_RW {

    MEPC(long initValue) {
        super(initValue);
    }

    @Override
    protected void write(long value) {
        super.write(value & (~3L));
    }
}
