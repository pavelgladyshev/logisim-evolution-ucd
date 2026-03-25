package com.cburch.logisim.riscv.cpu.csrs;

/**
 * Machine Interrupt Delegation Register (mideleg).
 * Each bit corresponds to an interrupt code. If bit N is set,
 * interrupt N is delegated from M-mode to S-mode when it occurs
 * in S-mode or U-mode.
 */
public class MIDELEG_CSR extends CSR_RW {
    public MIDELEG_CSR(long initValue) {
        super(initValue);
    }

    /**
     * Check if a specific interrupt code is delegated to S-mode.
     */
    public boolean isDelegated(int interruptCode) {
        return ((read() >> interruptCode) & 1) == 1;
    }
}
