package com.cburch.logisim.riscv;

import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer;
import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer.TlbResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TLB correctness and performance characteristics:
 * - No thrashing with megapages covering the same region
 * - Hits after population
 * - LRU eviction behavior
 * - HashMap consistency with the array state
 * - Statistics tracking
 */
class TlbPerformanceTest {

    private TranslationLookasideBuffer tlb;
    private static final int RWX = TranslationLookasideBuffer.PERM_R
                                 | TranslationLookasideBuffer.PERM_W
                                 | TranslationLookasideBuffer.PERM_X;
    private static final int RW  = TranslationLookasideBuffer.PERM_R
                                 | TranslationLookasideBuffer.PERM_W;

    @BeforeEach
    void setUp() {
        tlb = new TranslationLookasideBuffer();
    }

    // ========== Megapage Hit Tests ==========

    @Test
    void testMegapageCoversEntire4MBRange() {
        // Insert megapage at VA 0x00000000 → PA 0x00000000 (identity map)
        // PPN for megapage: PPN[1]=0, PPN[0]=0
        tlb.insert(0x00000000L, 0, RWX, true, 0);

        // Every 4KB page in 0-4MB should hit this megapage
        for (long va = 0; va < 0x400000; va += 0x1000) {
            TlbResult r = tlb.translate(va, 0);
            assertTrue(r.hit, "Megapage miss at VA=0x" + Long.toHexString(va));
            assertTrue(r.isMegapage);
            assertEquals(va, r.physicalAddress, "Wrong PA at VA=0x" + Long.toHexString(va));
        }
    }

    @Test
    void testMegapageNoThrashingWithSequentialAccess() {
        // Simulate kernel megapage: 0x00000000-0x003FFFFF identity mapped
        tlb.insert(0x00100000L, 0, RWX, true, 0);
        tlb.resetStats();

        // Access kernel code (0x100000), data (0x106000), stack (0x1FFFFC),
        // block device (0x200000) — all in the same megapage
        long[] addresses = {
            0x100000, 0x100004, 0x100008,  // code
            0x106000, 0x106004,             // data (proc_table)
            0x1FFFFC,                        // stack
            0x200000,                        // block device MMIO
            0x100000, 0x106000, 0x1FFFFC,   // back to code/data/stack
        };

        for (long va : addresses) {
            TlbResult r = tlb.translate(va, 0);
            assertTrue(r.hit, "Unexpected miss at VA=0x" + Long.toHexString(va));
        }

        assertEquals(addresses.length, tlb.getHits());
        assertEquals(0, tlb.getMisses(), "No misses expected — single megapage covers all");
    }

    @Test
    void testMegapageIdentityMapTranslation() {
        // PPN = 0 for identity map of first 4MB
        tlb.insert(0x00000000L, 0, RWX, true, 0);

        // Various offsets within the megapage
        assertEquals(0x00000000L, tlb.translate(0x00000000L, 0).physicalAddress);
        assertEquals(0x00100000L, tlb.translate(0x00100000L, 0).physicalAddress);
        assertEquals(0x001FFFFCL, tlb.translate(0x001FFFFCL, 0).physicalAddress);
        assertEquals(0x003FFFFFL, tlb.translate(0x003FFFFFL, 0).physicalAddress);
    }

    @Test
    void testMegapageNonIdentityMapTranslation() {
        // Map VA 0x00400000 → PA 0x00110000 (user slot at different physical address)
        // PPN[1] = PA >> 22 = 0, PPN[0] = (PA >> 12) & 0x3FF = 0x110
        // For megapage, PA = PPN[1] << 22 | VA[21:0]
        // So PPN[1] = 0x110000 >> 22 = 0 — that maps to PA 0x00000000 base
        // Actually for logOS: user VA 0x400000 maps to physical slot at 0x110000
        // PPN for megapage = PA >> 12 for 4MB-aligned, but slot isn't 4MB aligned
        // This would actually be done with 4KB pages, not megapages.
        // Test a properly aligned megapage: VA 0x00400000 → PA 0x00400000
        int ppn = 0x00400000 >> 12; // = 0x400
        // For megapage, PPN[1] = ppn >> 10 = 1
        tlb.insert(0x00400000L, ppn, RWX, true, 1);

        TlbResult r = tlb.translate(0x00400123L, 1);
        assertTrue(r.hit);
        assertEquals(0x00400123L, r.physicalAddress);
    }

    // ========== Hit After Population Tests ==========

    @Test
    void testAllInsertedPagesAreHitsImmediately() {
        // Insert 50 different 4KB pages
        for (int i = 0; i < 50; i++) {
            long va = 0x80000000L + (i * 0x1000L);
            tlb.insert(va, 0x1000 + i, RWX, false, 0);
        }

        tlb.resetStats();

        // All 50 should be hits
        for (int i = 0; i < 50; i++) {
            long va = 0x80000000L + (i * 0x1000L);
            TlbResult r = tlb.translate(va, 0);
            assertTrue(r.hit, "Entry " + i + " should be a hit");
            assertEquals((long)(0x1000 + i) << 12, r.physicalAddress);
        }

        assertEquals(50, tlb.getHits());
        assertEquals(0, tlb.getMisses());
    }

    @Test
    void testRepeatedAccessesSamePageAlwaysHit() {
        tlb.insert(0x80001000L, 0x100, RWX, false, 0);
        tlb.resetStats();

        // Access same page 1000 times with different offsets
        for (int i = 0; i < 1000; i++) {
            long va = 0x80001000L + (i & 0xFFF);
            TlbResult r = tlb.translate(va, 0);
            assertTrue(r.hit);
        }

        assertEquals(1000, tlb.getHits());
        assertEquals(0, tlb.getMisses());
        assertEquals(1.0, tlb.getHitRate(), 0.0001);
    }

    // ========== No Thrashing Tests ==========

    @Test
    void testNoThrashingWithTwoPagesAlternating() {
        // Two pages that would collide in a direct-mapped TLB
        // but should never thrash in a fully associative TLB
        long va1 = 0x80000000L; // VPN = 0x80000
        long va2 = 0x80040000L; // VPN = 0x80040 — same index in 64-entry direct-mapped
        // In direct-mapped: index = (VPN) & 63 = same for both if low bits match

        tlb.insert(va1, 0x100, RWX, false, 0);
        tlb.insert(va2, 0x200, RWX, false, 0);
        tlb.resetStats();

        // Alternate between them 100 times
        for (int i = 0; i < 100; i++) {
            assertTrue(tlb.translate(va1, 0).hit, "va1 miss on iteration " + i);
            assertTrue(tlb.translate(va2, 0).hit, "va2 miss on iteration " + i);
        }

        assertEquals(200, tlb.getHits());
        assertEquals(0, tlb.getMisses(), "FA TLB should never thrash on 2 pages");
    }

    @Test
    void testNoThrashingWith128PagesAllCached() {
        // Fill all 128 TLB entries
        for (int i = 0; i < 128; i++) {
            long va = 0x80000000L + (i * 0x1000L);
            tlb.insert(va, 0x1000 + i, RWX, false, 0);
        }

        assertEquals(128, tlb.getValidEntryCount());
        tlb.resetStats();

        // Access all 128 in reverse order — all should hit
        for (int i = 127; i >= 0; i--) {
            long va = 0x80000000L + (i * 0x1000L);
            TlbResult r = tlb.translate(va, 0);
            assertTrue(r.hit, "Entry " + i + " should hit");
        }

        assertEquals(128, tlb.getHits());
        assertEquals(0, tlb.getMisses());
    }

    // ========== LRU Eviction Tests ==========

    @Test
    void testLRUEvictsOldestEntry() {
        // Fill all 128 slots
        for (int i = 0; i < 128; i++) {
            long va = 0x80000000L + (i * 0x1000L);
            tlb.insert(va, i, RWX, false, 0);
        }

        // Access entry 0 to make it recently used
        tlb.translate(0x80000000L, 0);

        // Insert 129th entry — should evict entry 1 (oldest not recently accessed)
        long newVa = 0x90000000L;
        tlb.insert(newVa, 0xFFF, RWX, false, 0);

        // Entry 0 should still be cached (was recently used)
        assertTrue(tlb.translate(0x80000000L, 0).hit, "Entry 0 should survive (recently used)");

        // New entry should be cached
        assertTrue(tlb.translate(newVa, 0).hit, "New entry should be cached");

        // Entry 1 should have been evicted (oldest LRU)
        assertFalse(tlb.translate(0x80001000L, 0).hit, "Entry 1 should be evicted (LRU)");
    }

    @Test
    void testEvictionCountTracked() {
        // Fill all 128 slots
        for (int i = 0; i < 128; i++) {
            tlb.insert(0x80000000L + (i * 0x1000L), i, RWX, false, 0);
        }
        assertEquals(0, tlb.getEvictions(), "No evictions while filling");

        // Insert one more — should cause exactly 1 eviction
        tlb.insert(0x90000000L, 0xFFF, RWX, false, 0);
        assertEquals(1, tlb.getEvictions());
    }

    // ========== ASID Isolation Tests ==========

    @Test
    void testDifferentASIDsSameVABothCached() {
        long va = 0x00400000L; // USER_VA_BASE
        tlb.insert(va, 0x110, RWX, false, 0); // Process 0: slot at 0x110000
        tlb.insert(va, 0x118, RWX, false, 1); // Process 1: slot at 0x118000

        TlbResult r0 = tlb.translate(va, 0);
        TlbResult r1 = tlb.translate(va, 1);

        assertTrue(r0.hit);
        assertTrue(r1.hit);
        assertEquals(0x110000L, r0.physicalAddress);
        assertEquals(0x118000L, r1.physicalAddress);
    }

    @Test
    void testASIDInvalidationDoesntAffectOtherASIDs() {
        long va = 0x00400000L;
        tlb.insert(va, 0x110, RWX, false, 0);
        tlb.insert(va, 0x118, RWX, false, 1);

        tlb.invalidateASID(0);

        assertFalse(tlb.translate(va, 0).hit, "ASID 0 should be invalidated");
        assertTrue(tlb.translate(va, 1).hit, "ASID 1 should survive");
    }

    // ========== Mixed Megapage + 4KB Tests ==========

    @Test
    void testMegapageAnd4KBPagesCoexist() {
        // Kernel megapage at L1[0]: VA 0-4MB
        tlb.insert(0x00000000L, 0, RWX, true, 0);

        // User 4KB pages at VA 0x400000-0x407FFF (different L1 entry)
        for (int i = 0; i < 8; i++) {
            long va = 0x00400000L + (i * 0x1000L);
            tlb.insert(va, 0x110 + i, RWX | TranslationLookasideBuffer.PERM_U, false, 0);
        }

        // Kernel megapage should still hit
        TlbResult kr = tlb.translate(0x00100000L, 0);
        assertTrue(kr.hit, "Kernel megapage should still hit");
        assertTrue(kr.isMegapage);
        assertEquals(0x00100000L, kr.physicalAddress);

        // User pages should hit
        TlbResult ur = tlb.translate(0x00400000L, 0);
        assertTrue(ur.hit, "User page should hit");
        assertFalse(ur.isMegapage);
        assertEquals(0x00110000L, ur.physicalAddress);
    }

    @Test
    void testConsoleMMIOMegapageAtHighAddress() {
        // Console MMIO megapage: VA 0xFFC00000 → PA 0xFFC00000
        int ppn = 0xFFC00000 >>> 12; // = 0xFFC00
        tlb.insert(0xFFC00000L, ppn, RW, true, 0);

        // Access console output at 0xFFFF000C
        TlbResult r = tlb.translate(0xFFFF000CL, 0);
        assertTrue(r.hit);
        assertTrue(r.isMegapage);
        assertEquals(0xFFFF000CL, r.physicalAddress);
    }

    // ========== HashMap Consistency Tests ==========

    @Test
    void testHashMapConsistentAfterInsertAndEvict() {
        // Fill TLB, then evict, then verify all remaining entries are findable
        for (int i = 0; i < 128; i++) {
            tlb.insert(0x80000000L + (i * 0x1000L), i, RWX, false, 0);
        }

        // Evict 10 entries by inserting 10 new ones
        for (int i = 0; i < 10; i++) {
            tlb.insert(0x90000000L + (i * 0x1000L), 0x2000 + i, RWX, false, 0);
        }

        // Verify: for every valid entry, translate should hit
        int validCount = tlb.getValidEntryCount();
        assertEquals(128, validCount, "TLB should still be full");

        // Check all new entries are accessible
        for (int i = 0; i < 10; i++) {
            long va = 0x90000000L + (i * 0x1000L);
            assertTrue(tlb.translate(va, 0).hit, "New entry " + i + " should hit");
        }
    }

    @Test
    void testHashMapConsistentAfterInvalidateAll() {
        for (int i = 0; i < 50; i++) {
            tlb.insert(0x80000000L + (i * 0x1000L), i, RWX, false, 0);
        }

        tlb.invalidate();
        assertEquals(0, tlb.getValidEntryCount());

        // All should miss now
        for (int i = 0; i < 50; i++) {
            assertFalse(tlb.translate(0x80000000L + (i * 0x1000L), 0).hit);
        }
    }

    @Test
    void testHashMapConsistentAfterInvalidateAddress() {
        tlb.insert(0x80001000L, 0x100, RWX, false, 0);
        tlb.insert(0x80002000L, 0x200, RWX, false, 0);

        tlb.invalidateAddress(0x80001000L);

        assertFalse(tlb.translate(0x80001000L, 0).hit);
        assertTrue(tlb.translate(0x80002000L, 0).hit);

        // Re-insert at same address — should work correctly
        tlb.insert(0x80001000L, 0x300, RWX, false, 0);
        TlbResult r = tlb.translate(0x80001000L, 0);
        assertTrue(r.hit);
        assertEquals(0x00300000L, r.physicalAddress);
    }

    // ========== Statistics Tests ==========

    @Test
    void testStatsAccurate() {
        // 2 inserts
        tlb.insert(0x80001000L, 0x100, RWX, false, 0);
        tlb.insert(0x80002000L, 0x200, RWX, false, 0);
        assertEquals(2, tlb.getInserts());

        // 2 hits
        assertTrue(tlb.translate(0x80001000L, 0).hit);
        assertTrue(tlb.translate(0x80002000L, 0).hit);
        assertEquals(2, tlb.getHits());

        // 1 miss
        assertFalse(tlb.translate(0x80003000L, 0).hit);
        assertEquals(1, tlb.getMisses());

        // hit rate = 2/3
        assertEquals(2.0/3.0, tlb.getHitRate(), 0.0001);

        // reset
        tlb.resetStats();
        assertEquals(0, tlb.getHits());
        assertEquals(0, tlb.getMisses());
    }

    @Test
    void testDumpContainsValidEntries() {
        tlb.insert(0x00100000L, 0x100, RWX, false, 0);
        tlb.insert(0x00000000L, 0, RWX, true, 0);

        String dump = tlb.dump();
        assertTrue(dump.contains("4KB"), "Dump should show 4KB entry");
        assertTrue(dump.contains("MEGA"), "Dump should show megapage entry");
        assertTrue(dump.contains("RWX"), "Dump should show permissions");
        assertTrue(dump.contains("2/128"), "Dump should show 2 valid entries");
    }

    // ========== Simulated logOS Boot Pattern ==========

    @Test
    void testLogOSKernelBootPattern() {
        // Simulate the logOS boot: kernel megapage + console megapage
        // Kernel: VA 0-4MB → PA 0-4MB (identity, supervisor only, ASID=0)
        tlb.insert(0x00000000L, 0, RWX, false, 0); // will be overwritten as megapage
        tlb.invalidate();

        // Megapage insert
        tlb.insert(0x00100000L, 0, RWX, true, 0);

        // Console megapage: VA 0xFFC00000 → PA 0xFFC00000
        int consolePpn = 0xFFC00000 >>> 12;
        tlb.insert(0xFFC00000L, consolePpn, RW, true, 0);

        tlb.resetStats();

        // Simulate S-mode kernel running: sequential code fetch + data access + MMIO
        long[] pattern = {
            // Instruction fetches (sequential)
            0x100000, 0x100004, 0x100008, 0x10000C, 0x100010,
            // Data read (proc_table at 0x106000)
            0x1064C4, 0x1064C8,
            // Console MMIO write
            0xFFFF000C,
            // Back to code
            0x100014, 0x100018,
            // Data write (env string at 0x108000)
            0x108000, 0x108004, 0x108008,
            // Code again
            0x10001C,
            // Stack access
            0x1FFFF8, 0x1FFFF4,
        };

        int hits = 0;
        for (long va : pattern) {
            TlbResult r = tlb.translate(va, 0);
            if (r.hit) hits++;
        }

        // With megapages, everything should hit except possibly the first access
        // to a new megapage region. After 2 inserts (kernel + console),
        // all accesses within those regions should hit.
        assertEquals(pattern.length, hits,
            "All accesses should hit: kernel megapage covers 0-4MB, console megapage covers 0xFFC-0xFFF");
        assertEquals(0, tlb.getMisses());

        System.out.println(tlb.dump());
    }

    @Test
    void testLogOSUserProcessPattern() {
        // Kernel megapage
        tlb.insert(0x00100000L, 0, RWX, true, 0);
        // Console megapage
        tlb.insert(0xFFC00000L, 0xFFC00000 >>> 12, RW, true, 0);

        // User process slot 0: VA 0x400000-0x407FFF → PA 0x110000-0x117FFF
        for (int i = 0; i < 8; i++) {
            long va = 0x400000L + (i * 0x1000L);
            tlb.insert(va, 0x110 + i, RWX | TranslationLookasideBuffer.PERM_U, false, 0);
        }

        tlb.resetStats();

        // Simulate: user code at 0x400xxx, data at 0x402xxx, stack at 0x407xxx
        // Then ecall → kernel at 0x100xxx → console at 0xFFFF000C → sret → user
        long[] pattern = {
            // User code
            0x400000, 0x400004, 0x400008,
            // User data
            0x402000,
            // User stack
            0x407FF0,
            // ecall → kernel trap handler
            0x100200, 0x100204,
            // Kernel data
            0x106000,
            // Console MMIO
            0xFFFF000C,
            // sret → back to user
            0x40000C, 0x400010,
            // User stack
            0x407FEC,
        };

        for (long va : pattern) {
            TlbResult r = tlb.translate(va, 0);
            assertTrue(r.hit, "Should hit VA=0x" + Long.toHexString(va));
        }

        assertEquals(pattern.length, tlb.getHits());
        assertEquals(0, tlb.getMisses());
        System.out.println(tlb.dump());
    }
}
