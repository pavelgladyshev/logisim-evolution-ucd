package com.cburch.logisim.riscv;

import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer;
import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer.AccessType;
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
        tlb.translate(0x80001000L, 0);
        assertFalse(tlb.resultHit);
    }

    @Test
    void testTlbHitAfterInsert() {
        long va = 0x80001000L;
        int ppn = 0x00100;
        int perms = TranslationLookasideBuffer.PERM_R | TranslationLookasideBuffer.PERM_W | TranslationLookasideBuffer.PERM_X;
        tlb.insert(va, ppn, perms, false, 0);

        tlb.translate(va, 0);
        assertTrue(tlb.resultHit);
        assertEquals(0x00100000L, tlb.resultPA);
        assertEquals(perms, tlb.resultPerms);
        assertFalse(tlb.resultMega);
    }

    @Test
    void testTlbHitWithPageOffset() {
        long va = 0x80001ABCL;
        int ppn = 0x00200;
        int perms = TranslationLookasideBuffer.PERM_R;
        tlb.insert(va, ppn, perms, false, 0);

        tlb.translate(va, 0);
        assertTrue(tlb.resultHit);
        assertEquals(0x00200ABCL, tlb.resultPA);
    }

    @Test
    void testTlbMissWrongVPN() {
        long va1 = 0x80001000L;
        long va2 = 0x80002000L;
        int ppn = 0x00100;
        int perms = TranslationLookasideBuffer.PERM_R;
        tlb.insert(va1, ppn, perms, false, 0);

        tlb.translate(va2, 0);
        assertFalse(tlb.resultHit, "Expected TLB miss for different VPN");
    }

    @Test
    void testTlbMissWrongASID() {
        long va = 0x80001000L;
        int ppn = 0x00100;
        int perms = TranslationLookasideBuffer.PERM_R;
        tlb.insert(va, ppn, perms, false, 1); // ASID=1

        tlb.translate(va, 2); // query with ASID=2
        assertFalse(tlb.resultHit);
    }

    @Test
    void testTlbGlobalPageMatchesAnyASID() {
        long va = 0x80001000L;
        int ppn = 0x00100;
        int perms = TranslationLookasideBuffer.PERM_R | TranslationLookasideBuffer.PERM_G;
        tlb.insert(va, ppn, perms, false, 1);

        tlb.translate(va, 99); // different ASID
        assertTrue(tlb.resultHit);
    }

    @Test
    void testTlbMegapageTranslation() {
        long va = 0x80400123L;
        int ppn = 0x201 << 10; // PPN[1]=0x201
        int perms = TranslationLookasideBuffer.PERM_R | TranslationLookasideBuffer.PERM_W | TranslationLookasideBuffer.PERM_X;
        tlb.insert(va, ppn, perms, true, 0);

        tlb.translate(va, 0);
        assertTrue(tlb.resultHit);
        assertTrue(tlb.resultMega);
        assertEquals(0x80400123L, tlb.resultPA);
    }

    @Test
    void testTlbInvalidateAll() {
        long va = 0x80001000L;
        tlb.insert(va, 0x100, TranslationLookasideBuffer.PERM_R, false, 0);
        tlb.translate(va, 0);
        assertTrue(tlb.resultHit);

        tlb.invalidate();
        tlb.translate(va, 0);
        assertFalse(tlb.resultHit);
    }

    @Test
    void testTlbInvalidateByAddress() {
        long va1 = 0x80001000L;
        long va2 = 0x80002000L;
        tlb.insert(va1, 0x100, TranslationLookasideBuffer.PERM_R, false, 0);
        tlb.insert(va2, 0x200, TranslationLookasideBuffer.PERM_R, false, 0);

        tlb.invalidateAddress(va1);
        tlb.translate(va1, 0);
        assertFalse(tlb.resultHit);
        tlb.translate(va2, 0);
        assertTrue(tlb.resultHit);
    }

    @Test
    void testTlbInvalidateByASID() {
        long va1 = 0x80001000L;
        long va2 = 0x80002000L;
        tlb.insert(va1, 0x100, TranslationLookasideBuffer.PERM_R, false, 1);
        tlb.insert(va2, 0x200, TranslationLookasideBuffer.PERM_R, false, 2);

        tlb.invalidateASID(1);
        tlb.translate(va1, 1);
        assertFalse(tlb.resultHit);
        tlb.translate(va2, 2);
        assertTrue(tlb.resultHit);
    }

    @Test
    void testTlbGlobalNotInvalidatedByASID() {
        long va = 0x80001000L;
        int perms = TranslationLookasideBuffer.PERM_R | TranslationLookasideBuffer.PERM_G;
        tlb.insert(va, 0x100, perms, false, 1);

        tlb.invalidateASID(1);
        tlb.translate(va, 1);
        assertTrue(tlb.resultHit); // global pages survive ASID invalidation
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
        satp.PPN.set(0x80000);
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
        satp.ASID.set(0x1FF);
        satp.PPN.set(0x3FFFFF);

        assertEquals(1, satp.MODE.get());
        assertEquals(0x1FF, satp.ASID.get());
        assertEquals(0x3FFFFF, satp.PPN.get());
    }
}
