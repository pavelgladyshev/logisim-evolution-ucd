package com.cburch.logisim.riscv.cpu.csrs;

public class BITFIELD {

    private final CSR register;

    private final int startBitInclusive;
    private final int endBitInclusive;

    BITFIELD(CSR register, int startBitInclusive, int endBitInclusive) {
        this.startBitInclusive = startBitInclusive;
        this.endBitInclusive = endBitInclusive;
        this.register = register;
    }

    public long get() {
        return ((register.read() >> this.startBitInclusive) & (this.bitMask()));
    }

    public void set(long value) {
        long newValue = (value << startBitInclusive) & this.inPlaceBitMask();
        long zeroMaskedRegisterValue = register.read() & (~this.inPlaceBitMask());
        register.write(newValue | zeroMaskedRegisterValue);
    }

    private long length() {
        return endBitInclusive - startBitInclusive + 1;
    }

    private long bitMask() {
        return ((1L << this.length()) - 1);
    }

    private long inPlaceBitMask() {
        return (bitMask() << this.startBitInclusive);
    }
}
