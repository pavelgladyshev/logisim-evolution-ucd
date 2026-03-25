package com.cburch.logisim.riscv.cpu;

import java.util.HashMap;

/**
 * Fully associative TLB with 128 entries, LRU replacement,
 * and HashMap-accelerated lookup for simulation performance.
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

    // HashMap for O(1) lookup: key = (vpn << 9 | asid), value = TLB index
    // For megapages: key = (vpn1 << 19 | asid | 0x80000000)
    private final HashMap<Long, Integer> lookupMap = new HashMap<>();

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

    public static class TlbResult {
        public final boolean hit;
        public final long physicalAddress;
        public final int permissions;
        public final boolean isMegapage;

        private TlbResult(boolean hit, long physicalAddress, int permissions, boolean isMegapage) {
            this.hit = hit;
            this.physicalAddress = physicalAddress;
            this.permissions = permissions;
            this.isMegapage = isMegapage;
        }

        public static TlbResult miss() {
            return new TlbResult(false, 0, 0, false);
        }

        public static TlbResult hit(long physicalAddress, int permissions, boolean isMegapage) {
            return new TlbResult(true, physicalAddress, permissions, isMegapage);
        }
    }

    private long makeKey(int vaVpn, int asidVal, boolean isMega) {
        if (isMega) {
            return ((long)(vaVpn >> 10) << 19) | asidVal | 0x80000000L;
        }
        return ((long)vaVpn << 9) | asidVal;
    }

    /**
     * O(1) lookup using HashMap, with fallback linear scan for global pages.
     */
    public TlbResult translate(long virtualAddress, int currentAsid) {
        int vaVpn = (int) (virtualAddress >>> 12) & 0xFFFFF;
        accessCounter++;

        // Try exact match (4KB page)
        Integer idx = lookupMap.get(makeKey(vaVpn, currentAsid, false));
        if (idx != null && valid[idx] && vpn[idx] == vaVpn) {
            lastUsed[idx] = accessCounter;
            long pa = ((long) ppn[idx] << 12) | (virtualAddress & 0xFFF);
            return TlbResult.hit(pa, perms[idx], false);
        }

        // Try megapage match
        idx = lookupMap.get(makeKey(vaVpn, currentAsid, true));
        if (idx != null && valid[idx] && megapage[idx] && (vpn[idx] >> 10) == (vaVpn >> 10)) {
            lastUsed[idx] = accessCounter;
            long pa = ((long) (ppn[idx] >> 10) << 22) | (virtualAddress & 0x3FFFFF);
            return TlbResult.hit(pa, perms[idx], true);
        }

        // Fallback: linear scan for global entries (ASID-independent)
        for (int i = 0; i < TLB_SIZE; i++) {
            if (!valid[i]) continue;
            if ((perms[i] & PERM_G) == 0) continue; // skip non-global
            boolean tagMatch = megapage[i]
                ? (vpn[i] >> 10) == (vaVpn >> 10)
                : vpn[i] == vaVpn;
            if (tagMatch) {
                lastUsed[i] = accessCounter;
                long pa = megapage[i]
                    ? ((long) (ppn[i] >> 10) << 22) | (virtualAddress & 0x3FFFFF)
                    : ((long) ppn[i] << 12) | (virtualAddress & 0xFFF);
                return TlbResult.hit(pa, perms[i], megapage[i]);
            }
        }

        return TlbResult.miss();
    }

    /**
     * Insert a new entry, evicting LRU if full.
     */
    public void insert(long virtualAddress, int physicalPageNumber, int permissions,
                       boolean isMegapage, int entryAsid) {
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

        // Remove old entry from lookup map if being evicted
        if (valid[target]) {
            lookupMap.remove(makeKey(vpn[target], asid[target], megapage[target]));
        }

        int entryVpn = (int) (virtualAddress >>> 12) & 0xFFFFF;
        valid[target] = true;
        vpn[target] = entryVpn;
        ppn[target] = physicalPageNumber;
        perms[target] = permissions;
        megapage[target] = isMegapage;
        asid[target] = entryAsid;
        lastUsed[target] = accessCounter;

        // Add to lookup map (skip global entries — they're found via linear scan)
        if ((permissions & PERM_G) == 0) {
            lookupMap.put(makeKey(entryVpn, entryAsid, isMegapage), target);
        }
    }

    public void invalidate() {
        for (int i = 0; i < TLB_SIZE; i++) {
            valid[i] = false;
        }
        lookupMap.clear();
    }

    public void invalidateAddress(long virtualAddress) {
        int vaVpn = (int) (virtualAddress >>> 12) & 0xFFFFF;
        for (int i = 0; i < TLB_SIZE; i++) {
            if (!valid[i]) continue;
            boolean match = megapage[i]
                ? (vpn[i] >> 10) == (vaVpn >> 10)
                : vpn[i] == vaVpn;
            if (match) {
                lookupMap.remove(makeKey(vpn[i], asid[i], megapage[i]));
                valid[i] = false;
            }
        }
    }

    public void invalidateASID(int targetAsid) {
        for (int i = 0; i < TLB_SIZE; i++) {
            if (valid[i] && (perms[i] & PERM_G) == 0 && asid[i] == targetAsid) {
                lookupMap.remove(makeKey(vpn[i], asid[i], megapage[i]));
                valid[i] = false;
            }
        }
    }

    public void invalidateAddressAndASID(long virtualAddress, int targetAsid) {
        int vaVpn = (int) (virtualAddress >>> 12) & 0xFFFFF;
        for (int i = 0; i < TLB_SIZE; i++) {
            if (!valid[i]) continue;
            if ((perms[i] & PERM_G) != 0) continue;
            if (asid[i] != targetAsid) continue;
            boolean match = megapage[i]
                ? (vpn[i] >> 10) == (vaVpn >> 10)
                : vpn[i] == vaVpn;
            if (match) {
                lookupMap.remove(makeKey(vpn[i], asid[i], megapage[i]));
                valid[i] = false;
            }
        }
    }
}
