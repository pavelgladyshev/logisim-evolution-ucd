package com.cburch.logisim.riscv;

public class MSTATUS extends CSR_RW {

    protected BITFIELD MIE;
    protected BITFIELD MPIE;
    protected BITFIELD MPP;

    MSTATUS(long initValue) {
        super(initValue);
        MIE = new BITFIELD(this, 3,3);
        MPIE = new BITFIELD(this, 7,7);
        MPP = new BITFIELD(this, 11,12);

        MPP.set(PRIVILEGE_MODE.MACHINE.getValue());
    }

    @Override
    protected void write(long value) {
        super.write(value);
    }
}
