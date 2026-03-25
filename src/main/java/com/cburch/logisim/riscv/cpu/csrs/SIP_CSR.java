package com.cburch.logisim.riscv.cpu.csrs;

/**
 * Supervisor Interrupt Pending Register (sip).
 * This is a restricted view of MIP — exposes only S-mode interrupt pending bits:
 *   bit 1: SSIP (Supervisor Software Interrupt Pending)
 *   bit 5: STIP (Supervisor Timer Interrupt Pending) — read-only from S-mode
 *   bit 9: SEIP (Supervisor External Interrupt Pending) — read-only from S-mode
 *
 * For simplicity, we allow writes to SSIP (bit 1) only; STIP and SEIP are
 * read-only (set by M-mode firmware).
 */
public class SIP_CSR extends CSR {
    private final MIP_CSR mip;

    /** Mask of bits visible through sip */
    private static final long SIP_READ_MASK = (1L << 1) | (1L << 5) | (1L << 9);
    /** Only SSIP (bit 1) is writable from S-mode */
    private static final long SIP_WRITE_MASK = (1L << 1);

    public SIP_CSR(MIP_CSR mip) {
        this.mip = mip;
    }

    @Override
    public long read() {
        return mip.read() & SIP_READ_MASK;
    }

    @Override
    public void write(long value) {
        long current = mip.read();
        long newVal = (current & ~SIP_WRITE_MASK) | (value & SIP_WRITE_MASK);
        mip.write(newVal);
    }
}
