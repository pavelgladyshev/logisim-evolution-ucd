package com.cburch.logisim.riscv.cpu.csrs;

/**
 * Supervisor Interrupt Enable Register (sie).
 * This is a restricted view of MIE — exposes only S-mode interrupt enables:
 *   bit 1: SSIE (Supervisor Software Interrupt Enable)
 *   bit 5: STIE (Supervisor Timer Interrupt Enable)
 *   bit 9: SEIE (Supervisor External Interrupt Enable)
 */
public class SIE_CSR extends CSR {
    private final MIE_CSR mie;

    /** Mask of bits visible through sie */
    private static final long SIE_MASK = (1L << 1) | (1L << 5) | (1L << 9);

    public SIE_CSR(MIE_CSR mie) {
        this.mie = mie;
    }

    @Override
    public long read() {
        return mie.read() & SIE_MASK;
    }

    @Override
    public void write(long value) {
        long current = mie.read();
        long newVal = (current & ~SIE_MASK) | (value & SIE_MASK);
        mie.write(newVal);
    }
}
