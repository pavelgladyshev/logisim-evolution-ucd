package com.cburch.logisim.riscv.cpu.csrs;

/**
 * Machine Exception Delegation Register (medeleg).
 * Each bit corresponds to an exception code. If bit N is set,
 * exception N is delegated from M-mode to S-mode when it occurs
 * in S-mode or U-mode.
 */
public class MEDELEG_CSR extends CSR_RW {
    public MEDELEG_CSR(long initValue) {
        super(initValue);
    }

    /**
     * Check if a specific exception code is delegated to S-mode.
     */
    public boolean isDelegated(int exceptionCode) {
        return ((read() >> exceptionCode) & 1) == 1;
    }
}
