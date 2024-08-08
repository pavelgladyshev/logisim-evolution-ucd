package com.cburch.logisim.riscv.cpu.csrs;

public class MEPC_CSR extends CSR_RW {
    public MEPC_CSR(long initValue) {
        super(initValue);
    }

    @Override
    public void write(long value) {
        super.write(value & (~3L));
    }
}
