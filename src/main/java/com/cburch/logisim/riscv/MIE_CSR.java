package com.cburch.logisim.riscv;

public class MIE_CSR extends CSR_RW {
    protected BITFIELD MSIE;
    protected BITFIELD MTIE;
    protected BITFIELD MEIE;

    MIE_CSR(long initValue) {
        super(initValue);
        MSIE = new BITFIELD(this, 3, 3);
        MTIE = new BITFIELD(this, 7, 7);
        MEIE = new BITFIELD(this, 11, 11);
    }

    @Override
    protected void write(long value) {
        super.write(value);
    }
}