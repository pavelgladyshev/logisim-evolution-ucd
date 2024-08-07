package com.cburch.logisim.riscv;

public class MIP_CSR extends CSR_RW {
    protected BITFIELD MSIP;
    protected BITFIELD MTIP;
    protected BITFIELD MEIP;

    MIP_CSR(long initValue) {
        super(initValue);
        MSIP = new BITFIELD(this, 3, 3);
        MTIP = new BITFIELD(this, 7, 7);
        MEIP = new BITFIELD(this, 11, 11);
    }

    @Override
    protected void write(long value) {
        super.write(value);
    }
}