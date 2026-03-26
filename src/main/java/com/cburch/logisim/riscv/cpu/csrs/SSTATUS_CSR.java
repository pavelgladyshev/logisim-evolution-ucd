package com.cburch.logisim.riscv.cpu.csrs;

/**
 * Supervisor Status Register (sstatus).
 * This is a restricted view of MSTATUS — reads/writes are proxied to the
 * underlying MSTATUS register, exposing only the S-mode relevant bits:
 *   bit 1: SIE
 *   bit 5: SPIE
 *   bit 8: SPP
 *
 * All other bits read as zero and writes to them are ignored.
 */
public class SSTATUS_CSR extends CSR {
    private final MSTATUS_CSR mstatus;

    /** Mask of bits visible through sstatus: SIE(1), SPIE(5), SPP(8), SUM(18), MXR(19) */
    private static final long SSTATUS_MASK =
        (1L << 1) | (1L << 5) | (1L << 8) | (1L << 18) | (1L << 19);

    public SSTATUS_CSR(MSTATUS_CSR mstatus) {
        this.mstatus = mstatus;
    }

    @Override
    public long read() {
        return mstatus.read() & SSTATUS_MASK;
    }

    @Override
    public void write(long value) {
        long current = mstatus.read();
        long newVal = (current & ~SSTATUS_MASK) | (value & SSTATUS_MASK);
        mstatus.write(newVal);
    }
}
