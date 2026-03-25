package com.cburch.logisim.riscv.cpu.csrs;

/**
 * Supervisor Exception Program Counter (sepc).
 * Like MEPC, low 2 bits are masked to zero (4-byte alignment).
 */
public class SEPC_CSR extends CSR_RW {
    public SEPC_CSR(long initValue) {
        super(initValue);
    }

    @Override
    public void write(long value) {
        super.write(value & (~3L));
    }
}
