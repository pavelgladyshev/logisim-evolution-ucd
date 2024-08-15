package com.cburch.logisim.riscv.cpu.csrs;

public class MIE_CSR extends CSR_RW {
    public BITFIELD MSIE;
    public BITFIELD MTIE;
    public BITFIELD MEIE;

    public MIE_CSR(long initValue) {
        super(initValue);
        MSIE = new BITFIELD(this, 3, 3);
        MTIE = new BITFIELD(this, 7, 7);
        MEIE = new BITFIELD(this, 11, 11);
    }

    @Override
    public void write(long value) {
        super.write(value);
    }
}