package com.cburch.logisim.riscv;

public class MTVEC extends CSR_RW {

    protected BITFIELD MODE;
    protected BITFIELD BASE;

    MTVEC(long initValue) {
        super(initValue);
        MODE = new BITFIELD(this, 0, 1);
        BASE = new BITFIELD(this, 2,31);
    }

    protected static class MODE extends BITFIELD {
        MODE(CSR register, int startBitInclusive, int endBitInclusive) {
            super(register, startBitInclusive, endBitInclusive);
        }
        public Boolean isVectored() {
            return get() == 1;
        }
    }
}
