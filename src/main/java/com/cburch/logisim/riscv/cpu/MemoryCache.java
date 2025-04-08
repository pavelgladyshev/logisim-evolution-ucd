package com.cburch.logisim.riscv.cpu;

public class MemoryCache {
    private final boolean[] valid = new boolean[1024];
    private final long[] tags = new long[1024];
    private final long[] data = new long[1024];

    public void invalidate() {
        for (int i = 0; i < 1024; i++) {
            valid[i] = false;
            tags[i] = 0;
            data[i] = 0;
        }
    }

    private int index(long addr) {
        return (int) ((addr & 0xfff) >> 2);
    }

    public boolean isValid(long addr) {
        int idx = index(addr);
        return valid[idx] && tags[idx] == addr;
    }

    public long get(long addr) {
        if (!isValid(addr)) {
            throw new IllegalStateException("Cache miss at address: 0x" + Long.toHexString(addr));
        }
        return data[index(addr)];
    }

    public void update(long addr, long dataIn) {
        int idx = index(addr);
        valid[idx] = true;
        tags[idx] = addr;
        data[idx] = dataIn;
    }
}
