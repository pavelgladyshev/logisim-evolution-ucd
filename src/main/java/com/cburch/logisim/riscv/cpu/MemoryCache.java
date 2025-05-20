package com.cburch.logisim.riscv.cpu;

public class MemoryCache {
    private final boolean[] valid = new boolean[1024];
    private final long[] tags = new long[1024];
    private final long[] data = new long[1024];

    int hitCount = 0;
    int missCount = 0;

    public void invalidate() {
        for (int i = 0; i < 1024; i++) {
            valid[i] = false;
            tags[i] = 0;
            data[i] = 0;
        }
        resetStats();
    }

    private int index(long addr) {
        return (int) ((addr >> 2) & 0x3ff);
    }

    public boolean isValid(long addr) {
        int idx = index(addr);
        boolean hit = valid[idx] && tags[idx] == addr;
        if (hit) {
            hitCount++;
        } else {
            missCount++;
        }
        return hit;
    }


    public long get(long addr) {
        if (!isValid(addr)) {
            throw new IllegalStateException("Cache miss at address: 0x" + Long.toHexString(addr));
        }
        return data[index(addr)];
    }

    public void update(long addr, long dataIn) {
        //System.out.println("Update called on addr: 0x" + Long.toHexString(addr));

        int idx = index(addr);
        valid[idx] = true;
        tags[idx] = addr;
        data[idx] = dataIn;

        if (addr == 0x103f8 || addr == 0x103c8) {
            printStats();
        }
    }

    public void invalidateEntry(long addr) {
        int idx = index(addr);
        valid[idx] = false;
        tags[idx] = 0;
        data[idx] = 0;
    }

    public void printStats() {
        int total = hitCount + missCount;
        double hitRate = total > 0 ? (100.0 * hitCount / total) : 0.0;

       // System.out.println("[Cache Stats]");
        //System.out.println("Hits: " + hitCount);
        //System.out.println("Misses: " + missCount);
        //System.out.println("Total Accesses: " + total);
       // System.out.printf("Hit Rate: %.2f%%\n", hitRate);
    }

    public void resetStats() {
        hitCount = 0;
        missCount = 0;
    }

}
