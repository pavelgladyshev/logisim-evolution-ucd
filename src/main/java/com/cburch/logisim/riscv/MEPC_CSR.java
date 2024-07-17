package com.cburch.logisim.riscv;

public class MEPC_CSR extends CSR_RW {
    MEPC_CSR(long initValue) {
        super(initValue);
    }

    @Override
    protected void write(long value) {
        super.write(value & (~3L));
    }
}
