package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.csrs.SATP_CSR;

public class PageTableWalker {

    public enum PTWState {
        IDLE,
        L1_ADDR,     // Level-1 PTE address placed on bus, waiting for data
        L1_LATCH,    // Level-1 PTE data ready to latch
        L0_ADDR,     // Level-0 PTE address placed on bus, waiting for data
        L0_LATCH     // Level-0 PTE data ready to latch
    }

    // PTE bit positions
    private static final int PTE_V = 0x001;  // Valid
    private static final int PTE_R = 0x002;  // Read
    private static final int PTE_W = 0x004;  // Write
    private static final int PTE_X = 0x008;  // Execute
    private static final int PTE_U = 0x010;  // User
    private static final int PTE_G = 0x020;  // Global
    private static final int PTE_A = 0x040;  // Accessed
    private static final int PTE_D = 0x080;  // Dirty

    private final rv32imData hartData;
    private PTWState state;
    private long virtualAddress;
    private TranslationLookasideBuffer.AccessType accessType;
    private long level1PTE;
    private long pteAddress;

    public PageTableWalker(rv32imData hartData) {
        this.hartData = hartData;
        this.state = PTWState.IDLE;
    }

    public PTWState getState() {
        return state;
    }

    public boolean isIdle() {
        return state == PTWState.IDLE;
    }

    public void reset() {
        state = PTWState.IDLE;
    }

    /**
     * Begin a page table walk for the given virtual address.
     * Sets up the bus to read the level-1 PTE.
     */
    public void startWalk(long va, TranslationLookasideBuffer.AccessType type) {
        this.virtualAddress = va;
        this.accessType = type;

        SATP_CSR satp = hartData.getSatp();
        long rootPPN = satp.PPN.get();

        // VPN[1] = va[31:22], VPN[0] = va[21:12]
        int vpn1 = (int) ((va >> 22) & 0x3FF);

        // Level-1 PTE address = (SATP.PPN << 12) + (VPN[1] * 4)
        pteAddress = (rootPPN << 12) + (vpn1 * 4L);

        // Place level-1 PTE address on bus
        setBusForRead(pteAddress);
        state = PTWState.L1_ADDR;

        // Store faulting VA for potential page fault
        hartData.setPendingVirtualAddress(va);
        hartData.setPendingAccessType(type);
    }

    /**
     * Advance the page table walker state machine by one clock cycle.
     * Returns true if the walk is complete (translation resolved or page fault raised).
     */
    public boolean step(long dataIn, long waitRequest) {
        if (state == PTWState.IDLE) return true;

        switch (state) {
            case L1_ADDR:
                if (waitRequest == 1) return false;
                // Data should be available next cycle
                state = PTWState.L1_LATCH;
                return false;

            case L1_LATCH:
                if (waitRequest == 1) return false;
                level1PTE = dataIn & 0xFFFFFFFFL;
                return processLevel1PTE();

            case L0_ADDR:
                if (waitRequest == 1) return false;
                state = PTWState.L0_LATCH;
                return false;

            case L0_LATCH:
                if (waitRequest == 1) return false;
                long level0PTE = dataIn & 0xFFFFFFFFL;
                return processLevel0PTE(level0PTE);

            default:
                return true;
        }
    }

    private boolean processLevel1PTE() {
        // Check valid bit
        if ((level1PTE & PTE_V) == 0) {
            raisePageFault();
            return true;
        }

        // Check for reserved encoding: W=1, R=0
        if ((level1PTE & PTE_W) != 0 && (level1PTE & PTE_R) == 0) {
            raisePageFault();
            return true;
        }

        boolean isLeaf = ((level1PTE & PTE_R) != 0) || ((level1PTE & PTE_X) != 0);

        if (isLeaf) {
            // This is a megapage (4MB) at level 1
            return processMegapage();
        } else {
            // Non-leaf: descend to level 0
            int vpn0 = (int) ((virtualAddress >> 12) & 0x3FF);
            long ppn = (level1PTE >> 10) & 0x3FFFFF;
            pteAddress = (ppn << 12) + (vpn0 * 4L);
            setBusForRead(pteAddress);
            state = PTWState.L0_ADDR;
            return false;
        }
    }

    private boolean processMegapage() {
        // PPN[0] must be zero for megapage alignment
        long ppn0 = (level1PTE >> 10) & 0x3FF;
        if (ppn0 != 0) {
            raisePageFault();
            return true;
        }

        // Check permissions
        if (!checkPermissions(level1PTE)) {
            raisePageFault();
            return true;
        }

        // Build physical address for megapage:
        // PA = PPN[1] << 22 | VA[21:0]
        long ppn1 = (level1PTE >> 20) & 0xFFF;
        long pa = (ppn1 << 22) | (virtualAddress & 0x3FFFFF);

        // Cache in TLB
        int fullPPN = (int) ((level1PTE >> 10) & 0x3FFFFF);
        int perms = extractPermissions(level1PTE);
        hartData.getTlb().insert(virtualAddress, fullPPN, perms, true, hartData.getCurrentASID());

        completeTranslation(pa);
        return true;
    }

    private boolean processLevel0PTE(long pte) {
        // Check valid bit
        if ((pte & PTE_V) == 0) {
            raisePageFault();
            return true;
        }

        // Check for reserved encoding: W=1, R=0
        if ((pte & PTE_W) != 0 && (pte & PTE_R) == 0) {
            raisePageFault();
            return true;
        }

        // At level 0, PTE must be a leaf
        boolean isLeaf = ((pte & PTE_R) != 0) || ((pte & PTE_X) != 0);
        if (!isLeaf) {
            raisePageFault();
            return true;
        }

        // Check permissions
        if (!checkPermissions(pte)) {
            raisePageFault();
            return true;
        }

        // Build physical address:
        // PA = PPN << 12 | VA[11:0]
        long ppn = (pte >> 10) & 0x3FFFFF;
        long pa = (ppn << 12) | (virtualAddress & 0xFFF);

        // Cache in TLB
        int perms = extractPermissions(pte);
        hartData.getTlb().insert(virtualAddress, (int) ppn, perms, false, hartData.getCurrentASID());

        completeTranslation(pa);
        return true;
    }

    private boolean checkPermissions(long pte) {
        return switch (accessType) {
            case FETCH -> (pte & PTE_X) != 0;
            case LOAD -> (pte & PTE_R) != 0;
            case STORE -> (pte & PTE_W) != 0;
        };
    }

    private int extractPermissions(long pte) {
        int perms = 0;
        if ((pte & PTE_R) != 0) perms |= TranslationLookasideBuffer.PERM_R;
        if ((pte & PTE_W) != 0) perms |= TranslationLookasideBuffer.PERM_W;
        if ((pte & PTE_X) != 0) perms |= TranslationLookasideBuffer.PERM_X;
        if ((pte & PTE_U) != 0) perms |= TranslationLookasideBuffer.PERM_U;
        if ((pte & PTE_G) != 0) perms |= TranslationLookasideBuffer.PERM_G;
        if ((pte & PTE_A) != 0) perms |= TranslationLookasideBuffer.PERM_A;
        if ((pte & PTE_D) != 0) perms |= TranslationLookasideBuffer.PERM_D;
        return perms;
    }

    private void setBusForRead(long addr) {
        hartData.setFetching(false);
        hartData.setAddressing(false);
        hartData.setAddress(Value.createKnown(32, addr));
        hartData.setOutputData(0);
        hartData.setOutputDataWidth(0);
        hartData.setMemRead(Value.TRUE);
        hartData.setMemWrite(Value.FALSE);
    }

    private void raisePageFault() {
        state = PTWState.IDLE;
        hartData.handlePageFault(virtualAddress, accessType);
    }

    private void completeTranslation(long physicalAddress) {
        state = PTWState.IDLE;
        // Store the translated physical address for the caller to use
        hartData.setTranslatedPhysicalAddress(physicalAddress);
    }

    public long getVirtualAddress() {
        return virtualAddress;
    }

    public TranslationLookasideBuffer.AccessType getAccessType() {
        return accessType;
    }
}
