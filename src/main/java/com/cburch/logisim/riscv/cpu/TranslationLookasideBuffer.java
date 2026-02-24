package com.cburch.logisim.riscv.cpu;

public class TranslationLookasideBuffer {

    public static final int TLB_SIZE = 64;

    private final boolean[] valid = new boolean[TLB_SIZE];
    private final int[] vpn = new int[TLB_SIZE];       // 20-bit virtual page number (tag)
    private final int[] ppn = new int[TLB_SIZE];       // 22-bit physical page number
    private final int[] perms = new int[TLB_SIZE];     // permission bits packed: R|W|X|U|G|A|D
    private final boolean[] megapage = new boolean[TLB_SIZE];
    private final int[] asid = new int[TLB_SIZE];

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

    private int index(long virtualAddress) {
        return (int) ((virtualAddress >> 12) & (TLB_SIZE - 1));
    }

    public TlbResult translate(long virtualAddress, int currentAsid) {
        int idx = index(virtualAddress);
        if (!valid[idx]) {
            return TlbResult.miss();
        }

        int vaVpn = (int) (virtualAddress >>> 12) & 0xFFFFF; // 20-bit VPN

        // For megapages, only compare VPN[1] (top 10 bits of VPN)
        boolean tagMatch;
        if (megapage[idx]) {
            tagMatch = (vpn[idx] >> 10) == (vaVpn >> 10);
        } else {
            tagMatch = vpn[idx] == vaVpn;
        }

        // Check ASID match (global pages match any ASID)
        boolean asidMatch = ((perms[idx] & PERM_G) != 0) || (asid[idx] == currentAsid);

        if (tagMatch && asidMatch) {
            long pa;
            if (megapage[idx]) {
                // Megapage: PPN[1] from TLB, offset includes VPN[0] and page offset (22 bits)
                pa = ((long) (ppn[idx] >> 10) << 22) | (virtualAddress & 0x3FFFFF);
            } else {
                // Normal page: PPN from TLB, 12-bit page offset from VA
                pa = ((long) ppn[idx] << 12) | (virtualAddress & 0xFFF);
            }
            return TlbResult.hit(pa, perms[idx], megapage[idx]);
        }

        return TlbResult.miss();
    }

    public void insert(long virtualAddress, int physicalPageNumber, int permissions,
                       boolean isMegapage, int entryAsid) {
        int idx = index(virtualAddress);
        valid[idx] = true;
        vpn[idx] = (int) (virtualAddress >>> 12) & 0xFFFFF;
        ppn[idx] = physicalPageNumber;
        perms[idx] = permissions;
        megapage[idx] = isMegapage;
        asid[idx] = entryAsid;
    }

    public void invalidate() {
        for (int i = 0; i < TLB_SIZE; i++) {
            valid[i] = false;
        }
    }

    public void invalidateAddress(long virtualAddress) {
        int idx = index(virtualAddress);
        valid[idx] = false;
    }

    public void invalidateASID(int targetAsid) {
        for (int i = 0; i < TLB_SIZE; i++) {
            if (valid[i] && asid[i] == targetAsid && (perms[i] & PERM_G) == 0) {
                valid[i] = false;
            }
        }
    }

    public void invalidateAddressAndASID(long virtualAddress, int targetAsid) {
        int idx = index(virtualAddress);
        if (valid[idx] && asid[idx] == targetAsid) {
            valid[idx] = false;
        }
    }
}
