package com.cburch.logisim.riscv.cpu.csrs;

public class SATP_CSR extends CSR_RW {

    public BITFIELD MODE;
    public BITFIELD ASID;
    public BITFIELD PPN;

    public SATP_CSR(long initValue) {
        super(initValue);
        MODE = new BITFIELD(this, 31, 31);
        ASID = new BITFIELD(this, 22, 30);
        PPN = new BITFIELD(this, 0, 21);
    }

    public boolean isSV32Enabled() {
        return MODE.get() == 1;
    }

    public long getRootPageTableAddress() {
        return PPN.get() << 12;
    }
}
