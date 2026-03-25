package com.cburch.logisim.riscv.cpu.csrs;

/**
 * Supervisor Cause Register (scause).
 * Same structure as MCAUSE: INTERRUPT (bit 31) and EXCEPTION_CODE (bits 0-30).
 */
public class SCAUSE_CSR extends CSR_RW {

    public BITFIELD EXCEPTION_CODE;
    public BITFIELD INTERRUPT;

    public SCAUSE_CSR(long initValue) {
        super(initValue);
        EXCEPTION_CODE = new BITFIELD(this, 0, 30);
        INTERRUPT = new BITFIELD(this, 31, 31);
    }
}
