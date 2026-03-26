package com.cburch.logisim.riscv.cpu;

/**
 * Fully associative TLB with 128 entries, LRU replacement,
 * and fast-path caching for simulation performance.
 *
 * Hot-path optimization: a single-entry "last translation" cache avoids
 * any HashMap or array lookup for sequential instruction fetches within
 * the same page (the common case). This eliminates object allocation
 * and HashMap boxing overhead on the critical path.
 */
public class TranslationLookasideBuffer {

    public static final int TLB_SIZE = 128;

    private final boolean[] valid = new boolean[TLB_SIZE];
    private final int[] vpn = new int[TLB_SIZE];       // 20-bit virtual page number (tag)
    private final int[] ppn = new int[TLB_SIZE];       // 22-bit physical page number
    private final int[] perms = new int[TLB_SIZE];     // permission bits packed: R|W|X|U|G|A|D
    private final boolean[] megapage = new boolean[TLB_SIZE];
    private final int[] asid = new int[TLB_SIZE];
    private final long[] lastUsed = new long[TLB_SIZE]; // LRU timestamp
    private long accessCounter = 0;

    // ========== Fast-path: last translation cache ==========
    // Avoids HashMap/array lookup entirely for repeated accesses to the same page.
    // Fields are primitive — zero allocation on the hot path.
    private boolean lastValid = false;
    private int lastVpn;        // 20-bit VPN of last hit
    private int lastAsid;       // ASID of last hit
    private long lastPaBase;    // PA base (PPN << 12 or PPN[1] << 22 for megapage)
    private int lastPerms;      // permissions
    private boolean lastMega;   // was it a megapage?
    private int lastPageMask;   // 0xFFF for 4KB, 0x3FFFFF for megapage
    private int lastVpnMask;    // 0xFFFFF for 4KB, 0xFFC00 for megapage (top 10 bits)

    // ========== Direct-mapped page table (replaces HashMap) ==========
    // Array indexed by (VPN ^ ASID) & mask — much faster than HashMap.
    private static final int DM_SIZE = 256; // must be power of 2
    private static final int DM_MASK = DM_SIZE - 1;
    private final boolean[] dmValid = new boolean[DM_SIZE];
    private final int[] dmVpn = new int[DM_SIZE];
    private final int[] dmAsid = new int[DM_SIZE];
    private final int[] dmTlbIdx = new int[DM_SIZE]; // index into main TLB arrays

    // Statistics counters
    private long statHits = 0;
    private long statMisses = 0;
    private long statInserts = 0;
    private long statEvictions = 0;
    private long statFastHits = 0; // hits on last-translation cache

    // Permission bit positions within perms field
    public static final int PERM_R = 0x01;
    public static final int PERM_W = 0x02;
    public static final int PERM_X = 0x04;
    public static final int PERM_U = 0x08;
    public static final int PERM_G = 0x10;
    public static final int PERM_A = 0x20;
    public static final int PERM_D = 0x40;

    public enum AccessType {
        FETCH, LOAD, STORE
    }

    // ========== Allocation-free result passing ==========
    // Instead of returning new TlbResult objects, callers read these fields after translate().
    // This eliminates object allocation on the hot path.
    public boolean resultHit;
    public long resultPA;
    public int resultPerms;
    public boolean resultMega;

    /**
     * Zero-allocation translate. Result is stored in public fields:
     * resultHit, resultPA, resultPerms, resultMega.
     */
    public void translate(long virtualAddress, int currentAsid) {
        // Mask to 32 bits — RV32 addresses must not be sign-extended to 64-bit
        virtualAddress &= 0xFFFFFFFFL;
        int vaVpn = (int) (virtualAddress >>> 12) & 0xFFFFF;

        // === Fast path: last-translation cache ===
        if (lastValid) {
            if (lastMega
                ? ((vaVpn & lastVpnMask) == (lastVpn & lastVpnMask) && lastAsid == currentAsid)
                : (vaVpn == lastVpn && lastAsid == currentAsid)) {
                resultHit = true;
                resultPA = lastPaBase | (virtualAddress & lastPageMask);
                resultPerms = lastPerms;
                resultMega = lastMega;
                statHits++;
                statFastHits++;
                return;
            }
        }

        accessCounter++;

        // === Direct-mapped lookup for non-global entries ===
        // Try 4KB page
        int dmIdx = (vaVpn ^ currentAsid) & DM_MASK;
        if (dmValid[dmIdx] && dmVpn[dmIdx] == vaVpn && dmAsid[dmIdx] == currentAsid) {
            int ti = dmTlbIdx[dmIdx];
            if (valid[ti] && vpn[ti] == vaVpn && !megapage[ti]) {
                lastUsed[ti] = accessCounter;
                setLastCache(vaVpn, currentAsid, (long) ppn[ti] << 12, perms[ti], false);
                resultHit = true;
                resultPA = lastPaBase | (virtualAddress & 0xFFF);
                resultPerms = perms[ti];
                resultMega = false;
                statHits++;
                return;
            }
        }

        // Try megapage: index by VPN[1] (top 10 bits)
        int vpn1 = vaVpn >> 10;
        int dmIdxMega = (vpn1 ^ currentAsid ^ 0x100) & DM_MASK;
        if (dmValid[dmIdxMega] && (dmVpn[dmIdxMega] >> 10) == vpn1 && dmAsid[dmIdxMega] == currentAsid) {
            int ti = dmTlbIdx[dmIdxMega];
            if (valid[ti] && megapage[ti] && (vpn[ti] >> 10) == vpn1) {
                lastUsed[ti] = accessCounter;
                setLastCache(vaVpn, currentAsid, (long)(ppn[ti] >> 10) << 22, perms[ti], true);
                resultHit = true;
                resultPA = lastPaBase | (virtualAddress & 0x3FFFFF);
                resultPerms = perms[ti];
                resultMega = true;
                statHits++;
                return;
            }
        }

        // === Fallback: linear scan (for global entries and DM collisions) ===
        for (int i = 0; i < TLB_SIZE; i++) {
            if (!valid[i]) continue;

            boolean tagMatch = megapage[i]
                ? (vpn[i] >> 10) == vpn1
                : vpn[i] == vaVpn;
            if (!tagMatch) continue;

            boolean asidMatch = ((perms[i] & PERM_G) != 0) || (asid[i] == currentAsid);
            if (!asidMatch) continue;

            lastUsed[i] = accessCounter;
            if (megapage[i]) {
                setLastCache(vaVpn, currentAsid, (long)(ppn[i] >> 10) << 22, perms[i], true);
                resultHit = true;
                resultPA = lastPaBase | (virtualAddress & 0x3FFFFF);
            } else {
                setLastCache(vaVpn, currentAsid, (long) ppn[i] << 12, perms[i], false);
                resultHit = true;
                resultPA = lastPaBase | (virtualAddress & 0xFFF);
            }
            resultPerms = perms[i];
            resultMega = megapage[i];
            statHits++;
            return;
        }

        statMisses++;
        resultHit = false;
        resultPA = 0;
        resultPerms = 0;
        resultMega = false;
    }

    private void setLastCache(int vaVpn, int asidVal, long paBase, int perm, boolean mega) {
        lastValid = true;
        lastVpn = vaVpn;
        lastAsid = asidVal;
        lastPaBase = paBase;
        lastPerms = perm;
        lastMega = mega;
        lastPageMask = mega ? 0x3FFFFF : 0xFFF;
        lastVpnMask = mega ? 0xFFC00 : 0xFFFFF;
    }

    /**
     * Insert a new entry, evicting LRU if full.
     */
    public void insert(long virtualAddress, int physicalPageNumber, int permissions,
                       boolean isMegapage, int entryAsid) {
        virtualAddress &= 0xFFFFFFFFL;
        accessCounter++;

        // Find a free slot or the LRU entry
        int target = -1;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < TLB_SIZE; i++) {
            if (!valid[i]) {
                target = i;
                break;
            }
            if (lastUsed[i] < oldest) {
                oldest = lastUsed[i];
                target = i;
            }
        }

        // Remove old entry from DM cache if being evicted
        if (valid[target]) {
            removeDmEntry(vpn[target], asid[target], megapage[target]);
            statEvictions++;
        }
        statInserts++;

        int entryVpn = (int) (virtualAddress >>> 12) & 0xFFFFF;
        valid[target] = true;
        vpn[target] = entryVpn;
        ppn[target] = physicalPageNumber;
        perms[target] = permissions;
        megapage[target] = isMegapage;
        asid[target] = entryAsid;
        lastUsed[target] = accessCounter;

        // Add to DM cache (skip global entries — they're found via linear scan)
        if ((permissions & PERM_G) == 0) {
            addDmEntry(entryVpn, entryAsid, isMegapage, target);
        }

        // Invalidate last-translation cache (new entry may shadow it)
        lastValid = false;
    }

    private void addDmEntry(int entryVpn, int entryAsid, boolean isMega, int tlbIdx) {
        int idx;
        if (isMega) {
            idx = ((entryVpn >> 10) ^ entryAsid ^ 0x100) & DM_MASK;
        } else {
            idx = (entryVpn ^ entryAsid) & DM_MASK;
        }
        dmValid[idx] = true;
        dmVpn[idx] = entryVpn;
        dmAsid[idx] = entryAsid;
        dmTlbIdx[idx] = tlbIdx;
    }

    private void removeDmEntry(int entryVpn, int entryAsid, boolean isMega) {
        int idx;
        if (isMega) {
            idx = ((entryVpn >> 10) ^ entryAsid ^ 0x100) & DM_MASK;
        } else {
            idx = (entryVpn ^ entryAsid) & DM_MASK;
        }
        if (dmValid[idx] && dmVpn[idx] == entryVpn && dmAsid[idx] == entryAsid) {
            dmValid[idx] = false;
        }
    }

    public void invalidate() {
        for (int i = 0; i < TLB_SIZE; i++) valid[i] = false;
        for (int i = 0; i < DM_SIZE; i++) dmValid[i] = false;
        lastValid = false;
    }

    public void invalidateAddress(long virtualAddress) {
        virtualAddress &= 0xFFFFFFFFL;
        int vaVpn = (int) (virtualAddress >>> 12) & 0xFFFFF;
        for (int i = 0; i < TLB_SIZE; i++) {
            if (!valid[i]) continue;
            boolean match = megapage[i]
                ? (vpn[i] >> 10) == (vaVpn >> 10)
                : vpn[i] == vaVpn;
            if (match) {
                removeDmEntry(vpn[i], asid[i], megapage[i]);
                valid[i] = false;
            }
        }
        lastValid = false;
    }

    public void invalidateASID(int targetAsid) {
        for (int i = 0; i < TLB_SIZE; i++) {
            if (valid[i] && (perms[i] & PERM_G) == 0 && asid[i] == targetAsid) {
                removeDmEntry(vpn[i], asid[i], megapage[i]);
                valid[i] = false;
            }
        }
        lastValid = false;
    }

    public void invalidateAddressAndASID(long virtualAddress, int targetAsid) {
        virtualAddress &= 0xFFFFFFFFL;
        int vaVpn = (int) (virtualAddress >>> 12) & 0xFFFFF;
        for (int i = 0; i < TLB_SIZE; i++) {
            if (!valid[i]) continue;
            if ((perms[i] & PERM_G) != 0) continue;
            if (asid[i] != targetAsid) continue;
            boolean match = megapage[i]
                ? (vpn[i] >> 10) == (vaVpn >> 10)
                : vpn[i] == vaVpn;
            if (match) {
                removeDmEntry(vpn[i], asid[i], megapage[i]);
                valid[i] = false;
            }
        }
        lastValid = false;
    }

    // ========== Statistics and Diagnostics ==========

    public long getHits() { return statHits; }
    public long getMisses() { return statMisses; }
    public long getInserts() { return statInserts; }
    public long getEvictions() { return statEvictions; }
    public long getFastHits() { return statFastHits; }
    public long getTranslations() { return statHits + statMisses; }

    public double getHitRate() {
        long total = statHits + statMisses;
        return total == 0 ? 0.0 : (double) statHits / total;
    }

    public void resetStats() {
        statHits = 0;
        statMisses = 0;
        statInserts = 0;
        statEvictions = 0;
        statFastHits = 0;
    }

    public int getValidEntryCount() {
        int count = 0;
        for (int i = 0; i < TLB_SIZE; i++) {
            if (valid[i]) count++;
        }
        return count;
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("TLB: %d/%d valid, hits=%d (fast=%d) misses=%d (%.1f%% hit rate), inserts=%d evictions=%d\n",
            getValidEntryCount(), TLB_SIZE, statHits, statFastHits, statMisses,
            getHitRate() * 100, statInserts, statEvictions));
        for (int i = 0; i < TLB_SIZE; i++) {
            if (!valid[i]) continue;
            String type = megapage[i] ? "MEGA" : "4KB ";
            String permStr = ""
                + ((perms[i] & PERM_R) != 0 ? "R" : "-")
                + ((perms[i] & PERM_W) != 0 ? "W" : "-")
                + ((perms[i] & PERM_X) != 0 ? "X" : "-")
                + ((perms[i] & PERM_U) != 0 ? "U" : "-")
                + ((perms[i] & PERM_G) != 0 ? "G" : "-");
            long va, pa;
            if (megapage[i]) {
                va = ((long)(vpn[i] >> 10)) << 22;
                pa = ((long)(ppn[i] >> 10)) << 22;
            } else {
                va = ((long)vpn[i]) << 12;
                pa = ((long)ppn[i]) << 12;
            }
            sb.append(String.format("  [%3d] %s VA=0x%08x PA=0x%08x %s ASID=%d LRU=%d\n",
                i, type, va, pa, permStr, asid[i], lastUsed[i]));
        }
        return sb.toString();
    }
}
