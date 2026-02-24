package com.cburch.logisim.riscv;

import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer;
import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer.AccessType;
import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer.TlbResult;
import com.cburch.logisim.riscv.cpu.csrs.SATP_CSR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageTableWalkerTest {

    private TranslationLookasideBuffer tlb;

    @BeforeEach
    void setUp() {
        tlb = new TranslationLookasideBuffer();
    }

    // ========== TLB Tests ==========

    @Test
    void testTlbMissOnEmptyTlb() {
        TlbResult result = tlb.translate(0x80001000L, 0);
        assertFalse(result.hit);
    }

    @Test
    void testTlbHitAfterInsert() {
        // Insert mapping: VA 0x80001000 → PPN 0x00100 (PA = 0x00100000 + 0x000 offset)
        long va = 0x80001000L;
        int ppn = 0x00100;
        int perms = TranslationLookasideBuffer.PERM_R | TranslationLookasideBuffer.PERM_W | TranslationLookasideBuffer.PERM_X;
        tlb.insert(va, ppn, perms, false, 0);

        TlbResult result = tlb.translate(va, 0);
        assertTrue(result.hit);
        assertEquals(0x00100000L, result.physicalAddress);
        assertEquals(perms, result.permissions);
        assertFalse(result.isMegapage);
    }

    @Test
    void testTlbHitWithPageOffset() {
        // VA = 0x80001ABC → PA should be PPN << 12 | 0xABC
        long va = 0x80001ABCL;
        int ppn = 0x00200;
        int perms = TranslationLookasideBuffer.PERM_R;
        tlb.insert(va, ppn, perms, false, 0);

        TlbResult result = tlb.translate(va, 0);
        assertTrue(result.hit);
        assertEquals(0x00200ABCL, result.physicalAddress);
    }

    @Test
    void testTlbMissWrongVPN() {
        long va1 = 0x80001000L;
        long va2 = 0x80002000L; // different page
        int ppn = 0x00100;
        int perms = TranslationLookasideBuffer.PERM_R;
        tlb.insert(va1, ppn, perms, false, 0);

        TlbResult result = tlb.translate(va2, 0);
        // May or may not be a miss depending on index collision
        // but the tag won't match if they have different VPNs
        if (result.hit) {
            // If they happen to map to different indices, this won't happen
            // If they map to same index, the stored VPN won't match va2
            fail("Expected TLB miss for different VPN");
        }
    }

    @Test
    void testTlbMissWrongASID() {
        long va = 0x80001000L;
        int ppn = 0x00100;
        int perms = TranslationLookasideBuffer.PERM_R;
        tlb.insert(va, ppn, perms, false, 1); // ASID=1

        TlbResult result = tlb.translate(va, 2); // query with ASID=2
        assertFalse(result.hit);
    }

    @Test
    void testTlbGlobalPageMatchesAnyASID() {
        long va = 0x80001000L;
        int ppn = 0x00100;
        int perms = TranslationLookasideBuffer.PERM_R | TranslationLookasideBuffer.PERM_G;
        tlb.insert(va, ppn, perms, false, 1);

        TlbResult result = tlb.translate(va, 99); // different ASID
        assertTrue(result.hit);
    }

    @Test
    void testTlbMegapageTranslation() {
        // Megapage: 4MB mapping. VA 0x80400000 → PPN[1]=0x201, megapage
        // PA should be PPN[1] << 22 | VA[21:0]
        long va = 0x80400123L;
        int ppn = 0x201 << 10; // PPN[1]=0x201, PPN[0]=0 (aligned megapage)
        int perms = TranslationLookasideBuffer.PERM_R | TranslationLookasideBuffer.PERM_W | TranslationLookasideBuffer.PERM_X;
        tlb.insert(va, ppn, perms, true, 0);

        TlbResult result = tlb.translate(va, 0);
        assertTrue(result.hit);
        assertTrue(result.isMegapage);
        // PA = PPN[1] << 22 | VA[21:0] = 0x201 << 22 | 0x000123 = 0x80400123
        assertEquals(0x80400123L, result.physicalAddress);
    }

    @Test
    void testTlbInvalidateAll() {
        long va = 0x80001000L;
        tlb.insert(va, 0x100, TranslationLookasideBuffer.PERM_R, false, 0);
        assertTrue(tlb.translate(va, 0).hit);

        tlb.invalidate();
        assertFalse(tlb.translate(va, 0).hit);
    }

    @Test
    void testTlbInvalidateByAddress() {
        long va1 = 0x80001000L;  // index = 1
        long va2 = 0x80002000L;  // index = 2 (different TLB slot)
        tlb.insert(va1, 0x100, TranslationLookasideBuffer.PERM_R, false, 0);
        tlb.insert(va2, 0x200, TranslationLookasideBuffer.PERM_R, false, 0);

        tlb.invalidateAddress(va1);
        assertFalse(tlb.translate(va1, 0).hit);
        // va2 should still be valid (different index)
        assertTrue(tlb.translate(va2, 0).hit);
    }

    @Test
    void testTlbInvalidateByASID() {
        long va1 = 0x80001000L;  // index = 1
        long va2 = 0x80002000L;  // index = 2
        tlb.insert(va1, 0x100, TranslationLookasideBuffer.PERM_R, false, 1); // ASID=1
        tlb.insert(va2, 0x200, TranslationLookasideBuffer.PERM_R, false, 2); // ASID=2

        tlb.invalidateASID(1);
        assertFalse(tlb.translate(va1, 1).hit);
        assertTrue(tlb.translate(va2, 2).hit); // different ASID, still valid
    }

    @Test
    void testTlbGlobalNotInvalidatedByASID() {
        long va = 0x80001000L;
        int perms = TranslationLookasideBuffer.PERM_R | TranslationLookasideBuffer.PERM_G;
        tlb.insert(va, 0x100, perms, false, 1);

        tlb.invalidateASID(1);
        assertTrue(tlb.translate(va, 1).hit); // global pages survive ASID invalidation
    }

    // ========== SATP CSR Tests ==========

    @Test
    void testSatpModeDisabled() {
        SATP_CSR satp = new SATP_CSR(0);
        assertFalse(satp.isSV32Enabled());
    }

    @Test
    void testSatpModeEnabled() {
        SATP_CSR satp = new SATP_CSR(0);
        satp.MODE.set(1);
        assertTrue(satp.isSV32Enabled());
    }

    @Test
    void testSatpRootPageTableAddress() {
        SATP_CSR satp = new SATP_CSR(0);
        satp.PPN.set(0x80000); // PPN = 0x80000
        assertEquals(0x80000000L, satp.getRootPageTableAddress());
    }

    @Test
    void testSatpASID() {
        SATP_CSR satp = new SATP_CSR(0);
        satp.ASID.set(5);
        assertEquals(5, satp.ASID.get());
    }

    @Test
    void testSatpFieldIndependence() {
        SATP_CSR satp = new SATP_CSR(0);
        satp.MODE.set(1);
        satp.ASID.set(0x1FF);  // max 9-bit ASID
        satp.PPN.set(0x3FFFFF); // max 22-bit PPN

        assertEquals(1, satp.MODE.get());
        assertEquals(0x1FF, satp.ASID.get());
        assertEquals(0x3FFFFF, satp.PPN.get());
    }
}
