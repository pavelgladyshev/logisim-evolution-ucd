package com.cburch.logisim.riscv.cpu.csrs;

/**
 * Supervisor Trap Vector Base Address Register (stvec).
 * Same structure as MTVEC: MODE (bits 0-1) and BASE (bits 2-31).
 */
public class STVEC_CSR extends CSR_RW {

    public BITFIELD MODE;
    public BITFIELD BASE;

    public STVEC_CSR(long initValue) {
        super(initValue);
        MODE = new BITFIELD(this, 0, 1);
        BASE = new BITFIELD(this, 2, 31);
    }
}
