package com.cburch.logisim.riscv.cpu.csrs;

public class MIP_CSR extends CSR_RW {
    public BITFIELD MSIP;
    public BITFIELD MTIP;
    public BITFIELD MEIP;

    public MIP_CSR(long initValue) {
        super(initValue);
        MSIP = new BITFIELD(this, 3, 3);
        MTIP = new BITFIELD(this, 7, 7);
        MEIP = new BITFIELD(this, 11, 11);
    }

    @Override
    public void write(long value) {
        super.write(value);
    }
}