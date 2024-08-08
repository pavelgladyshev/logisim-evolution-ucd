package com.cburch.logisim.riscv.cpu.csrs;

public class MTVEC_CSR extends CSR_RW {

    public BITFIELD MODE;
    public BITFIELD BASE;

    public MTVEC_CSR(long initValue) {
        super(initValue);
        MODE = new MODE(this, 0, 1);
        BASE = new BITFIELD(this, 2,31);
    }

    public static class MODE extends BITFIELD {
        MODE(CSR register, int startBitInclusive, int endBitInclusive) {
            super(register, startBitInclusive, endBitInclusive);
        }
        public Boolean isVectored() {
            return get() == 1;
        }
    }
}
