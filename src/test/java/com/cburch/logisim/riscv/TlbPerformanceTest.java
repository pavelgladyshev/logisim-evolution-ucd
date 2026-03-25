package com.cburch.logisim.riscv;

import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TLB correctness and performance:
 * - No thrashing with megapages
 * - Hits after population
 * - Fast-path cache effectiveness
 * - LRU eviction
 * - ASID isolation
 * - Statistics
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
        tlb.insert(0x00000000L, 0, RWX, true, 0);

        for (long va = 0; va < 0x400000; va += 0x1000) {
            tlb.translate(va, 0);
            assertTrue(tlb.resultHit, "Megapage miss at VA=0x" + Long.toHexString(va));
            assertTrue(tlb.resultMega);
            assertEquals(va, tlb.resultPA, "Wrong PA at VA=0x" + Long.toHexString(va));
        }
    }

    @Test
    void testMegapageNoThrashingWithSequentialAccess() {
        tlb.insert(0x00100000L, 0, RWX, true, 0);
        tlb.resetStats();

        long[] addresses = {
            0x100000, 0x100004, 0x100008,
            0x106000, 0x106004,
            0x1FFFFC,
            0x200000,
            0x100000, 0x106000, 0x1FFFFC,
        };

        for (long va : addresses) {
            tlb.translate(va, 0);
            assertTrue(tlb.resultHit, "Unexpected miss at VA=0x" + Long.toHexString(va));
        }

        assertEquals(addresses.length, tlb.getHits());
        assertEquals(0, tlb.getMisses());
    }

    @Test
    void testMegapageIdentityMapTranslation() {
        tlb.insert(0x00000000L, 0, RWX, true, 0);

        tlb.translate(0x00000000L, 0);
        assertEquals(0x00000000L, tlb.resultPA);
        tlb.translate(0x00100000L, 0);
        assertEquals(0x00100000L, tlb.resultPA);
        tlb.translate(0x001FFFFCL, 0);
        assertEquals(0x001FFFFCL, tlb.resultPA);
        tlb.translate(0x003FFFFFL, 0);
        assertEquals(0x003FFFFFL, tlb.resultPA);
    }

    // ========== Fast-Path Cache Tests ==========

    @Test
    void testFastPathHitsOnSequentialFetch() {
        // Simulate sequential instruction fetch — same page, incrementing offset
        tlb.insert(0x00100000L, 0x100, RWX, false, 0);
        tlb.resetStats();

        // 100 fetches within the same 4KB page
        for (int i = 0; i < 100; i++) {
            tlb.translate(0x00100000L + (i * 4), 0);
            assertTrue(tlb.resultHit);
        }

        assertEquals(100, tlb.getHits());
        // First access goes through DM/linear scan, rest should be fast-path
        assertTrue(tlb.getFastHits() >= 99,
            "Expected >= 99 fast hits, got " + tlb.getFastHits());
    }

    @Test
    void testFastPathHitsOnMegapageSamePage() {
        tlb.insert(0x00100000L, 0, RWX, true, 0);
        tlb.resetStats();

        // Sequential code within megapage
        for (int i = 0; i < 200; i++) {
            tlb.translate(0x00100000L + (i * 4), 0);
            assertTrue(tlb.resultHit);
        }

        assertTrue(tlb.getFastHits() >= 199,
            "Megapage sequential access should be fast-path. Got " + tlb.getFastHits());
    }

    @Test
    void testFastPathInvalidatedOnInsert() {
        tlb.insert(0x00100000L, 0x100, RWX, false, 0);

        // Prime the fast-path cache
        tlb.translate(0x00100000L, 0);
        assertTrue(tlb.resultHit);

        // Insert new entry — fast-path should be invalidated
        tlb.insert(0x00200000L, 0x200, RWX, false, 0);

        // Next translate should still work but not via fast-path
        tlb.resetStats();
        tlb.translate(0x00100000L, 0);
        assertTrue(tlb.resultHit);
        assertEquals(0, tlb.getFastHits(), "After insert, first access should not be fast-path");
    }

    @Test
    void testFastPathInvalidatedOnSfenceVma() {
        tlb.insert(0x00100000L, 0x100, RWX, false, 0);
        tlb.translate(0x00100000L, 0); // prime fast-path

        tlb.invalidate();

        tlb.translate(0x00100000L, 0);
        assertFalse(tlb.resultHit, "After invalidate, should miss");
    }

    // ========== Hit After Population ==========

    @Test
    void testAllInsertedPagesAreHits() {
        for (int i = 0; i < 50; i++) {
            long va = 0x80000000L + (i * 0x1000L);
            tlb.insert(va, 0x1000 + i, RWX, false, 0);
        }

        tlb.resetStats();

        for (int i = 0; i < 50; i++) {
            long va = 0x80000000L + (i * 0x1000L);
            tlb.translate(va, 0);
            assertTrue(tlb.resultHit, "Entry " + i + " should be a hit");
            assertEquals((long)(0x1000 + i) << 12, tlb.resultPA);
        }

        assertEquals(50, tlb.getHits());
        assertEquals(0, tlb.getMisses());
    }

    @Test
    void testRepeatedAccessesSamePageAlwaysHit() {
        tlb.insert(0x80001000L, 0x100, RWX, false, 0);
        tlb.resetStats();

        for (int i = 0; i < 1000; i++) {
            long va = 0x80001000L + (i & 0xFFF);
            tlb.translate(va, 0);
            assertTrue(tlb.resultHit);
        }

        assertEquals(1000, tlb.getHits());
        assertEquals(0, tlb.getMisses());
    }

    // ========== No Thrashing ==========

    @Test
    void testNoThrashingWithTwoPagesAlternating() {
        long va1 = 0x80000000L;
        long va2 = 0x80040000L;

        tlb.insert(va1, 0x100, RWX, false, 0);
        tlb.insert(va2, 0x200, RWX, false, 0);
        tlb.resetStats();

        for (int i = 0; i < 100; i++) {
            tlb.translate(va1, 0);
            assertTrue(tlb.resultHit, "va1 miss on iteration " + i);
            tlb.translate(va2, 0);
            assertTrue(tlb.resultHit, "va2 miss on iteration " + i);
        }

        assertEquals(200, tlb.getHits());
        assertEquals(0, tlb.getMisses());
    }

    @Test
    void testNoThrashingWith128PagesAllCached() {
        for (int i = 0; i < 128; i++) {
            long va = 0x80000000L + (i * 0x1000L);
            tlb.insert(va, 0x1000 + i, RWX, false, 0);
        }

        assertEquals(128, tlb.getValidEntryCount());
        tlb.resetStats();

        for (int i = 127; i >= 0; i--) {
            long va = 0x80000000L + (i * 0x1000L);
            tlb.translate(va, 0);
            assertTrue(tlb.resultHit, "Entry " + i + " should hit");
        }

        assertEquals(128, tlb.getHits());
        assertEquals(0, tlb.getMisses());
    }

    // ========== LRU Eviction ==========

    @Test
    void testLRUEvictsOldestEntry() {
        for (int i = 0; i < 128; i++) {
            long va = 0x80000000L + (i * 0x1000L);
            tlb.insert(va, i, RWX, false, 0);
        }

        // Touch entry 0 to make it recently used
        tlb.translate(0x80000000L, 0);

        // Insert 129th — should evict entry 1 (LRU)
        long newVa = 0x90000000L;
        tlb.insert(newVa, 0xFFF, RWX, false, 0);

        tlb.translate(0x80000000L, 0);
        assertTrue(tlb.resultHit, "Entry 0 should survive (recently used)");

        tlb.translate(newVa, 0);
        assertTrue(tlb.resultHit, "New entry should be cached");

        tlb.translate(0x80001000L, 0);
        assertFalse(tlb.resultHit, "Entry 1 should be evicted (LRU)");
    }

    @Test
    void testEvictionCountTracked() {
        for (int i = 0; i < 128; i++) {
            tlb.insert(0x80000000L + (i * 0x1000L), i, RWX, false, 0);
        }
        assertEquals(0, tlb.getEvictions());

        tlb.insert(0x90000000L, 0xFFF, RWX, false, 0);
        assertEquals(1, tlb.getEvictions());
    }

    // ========== ASID Isolation ==========

    @Test
    void testDifferentASIDsSameVABothCached() {
        long va = 0x00400000L;
        tlb.insert(va, 0x110, RWX, false, 0);
        tlb.insert(va, 0x118, RWX, false, 1);

        tlb.translate(va, 0);
        assertTrue(tlb.resultHit);
        assertEquals(0x110000L, tlb.resultPA);

        tlb.translate(va, 1);
        assertTrue(tlb.resultHit);
        assertEquals(0x118000L, tlb.resultPA);
    }

    @Test
    void testASIDInvalidation() {
        long va = 0x00400000L;
        tlb.insert(va, 0x110, RWX, false, 0);
        tlb.insert(va, 0x118, RWX, false, 1);

        tlb.invalidateASID(0);

        tlb.translate(va, 0);
        assertFalse(tlb.resultHit);
        tlb.translate(va, 1);
        assertTrue(tlb.resultHit);
    }

    // ========== Mixed Megapage + 4KB ==========

    @Test
    void testMegapageAnd4KBPagesCoexist() {
        tlb.insert(0x00000000L, 0, RWX, true, 0);

        for (int i = 0; i < 8; i++) {
            long va = 0x00400000L + (i * 0x1000L);
            tlb.insert(va, 0x110 + i, RWX | TranslationLookasideBuffer.PERM_U, false, 0);
        }

        tlb.translate(0x00100000L, 0);
        assertTrue(tlb.resultHit, "Kernel megapage should hit");
        assertTrue(tlb.resultMega);
        assertEquals(0x00100000L, tlb.resultPA);

        tlb.translate(0x00400000L, 0);
        assertTrue(tlb.resultHit, "User page should hit");
        assertFalse(tlb.resultMega);
        assertEquals(0x00110000L, tlb.resultPA);
    }

    @Test
    void testConsoleMMIOMegapage() {
        int ppn = 0xFFC00000 >>> 12;
        tlb.insert(0xFFC00000L, ppn, RW, true, 0);

        tlb.translate(0xFFFF000CL, 0);
        assertTrue(tlb.resultHit);
        assertTrue(tlb.resultMega);
        assertEquals(0xFFFF000CL, tlb.resultPA);
    }

    // ========== Statistics ==========

    @Test
    void testStatsAccurate() {
        tlb.insert(0x80001000L, 0x100, RWX, false, 0);
        tlb.insert(0x80002000L, 0x200, RWX, false, 0);
        assertEquals(2, tlb.getInserts());

        tlb.translate(0x80001000L, 0);
        assertTrue(tlb.resultHit);
        tlb.translate(0x80002000L, 0);
        assertTrue(tlb.resultHit);
        assertEquals(2, tlb.getHits());

        tlb.translate(0x80003000L, 0);
        assertFalse(tlb.resultHit);
        assertEquals(1, tlb.getMisses());

        assertEquals(2.0/3.0, tlb.getHitRate(), 0.0001);
    }

    @Test
    void testDumpContainsEntries() {
        tlb.insert(0x00100000L, 0x100, RWX, false, 0);
        tlb.insert(0x00000000L, 0, RWX, true, 0);

        String dump = tlb.dump();
        assertTrue(dump.contains("4KB"), "Dump should show 4KB entry");
        assertTrue(dump.contains("MEGA"), "Dump should show megapage");
        assertTrue(dump.contains("RWX"), "Dump should show permissions");
        assertTrue(dump.contains("2/128"), "Dump should show 2 valid");
    }

    // ========== Simulated logOS Boot Pattern ==========

    @Test
    void testLogOSKernelBootPattern() {
        // Kernel megapage + console megapage
        tlb.insert(0x00100000L, 0, RWX, true, 0);
        tlb.insert(0xFFC00000L, 0xFFC00000 >>> 12, RW, true, 0);

        tlb.resetStats();

        long[] pattern = {
            0x100000, 0x100004, 0x100008, 0x10000C, 0x100010,
            0x1064C4, 0x1064C8,
            0xFFFF000C,
            0x100014, 0x100018,
            0x108000, 0x108004, 0x108008,
            0x10001C,
            0x1FFFF8, 0x1FFFF4,
        };

        int hits = 0;
        for (long va : pattern) {
            tlb.translate(va, 0);
            if (tlb.resultHit) hits++;
        }

        assertEquals(pattern.length, hits);
        assertEquals(0, tlb.getMisses());
        assertTrue(tlb.getFastHits() > 0, "Sequential code should use fast-path");
        System.out.println(tlb.dump());
    }

    @Test
    void testLogOSUserProcessPattern() {
        // Kernel megapage
        tlb.insert(0x00100000L, 0, RWX, true, 0);
        // Console megapage
        tlb.insert(0xFFC00000L, 0xFFC00000 >>> 12, RW, true, 0);
        // User 4KB pages
        for (int i = 0; i < 8; i++) {
            long va = 0x400000L + (i * 0x1000L);
            tlb.insert(va, 0x110 + i, RWX | TranslationLookasideBuffer.PERM_U, false, 0);
        }

        tlb.resetStats();

        long[] pattern = {
            0x400000, 0x400004, 0x400008,
            0x402000,
            0x407FF0,
            0x100200, 0x100204,
            0x106000,
            0xFFFF000C,
            0x40000C, 0x400010,
            0x407FEC,
        };

        for (long va : pattern) {
            tlb.translate(va, 0);
            assertTrue(tlb.resultHit, "Should hit VA=0x" + Long.toHexString(va));
        }

        assertEquals(pattern.length, tlb.getHits());
        assertEquals(0, tlb.getMisses());
        System.out.println(tlb.dump());
    }

    // ========== Performance Benchmark ==========

    @Test
    void testTranslatePerformance() {
        // Measure raw translate throughput with fast-path hits
        tlb.insert(0x00100000L, 0, RWX, true, 0);

        // Warm up
        for (int i = 0; i < 1000; i++) {
            tlb.translate(0x00100000L + (i * 4), 0);
        }

        tlb.resetStats();
        long start = System.nanoTime();
        int iterations = 1_000_000;
        for (int i = 0; i < iterations; i++) {
            tlb.translate(0x00100000L + ((i & 0xFF) * 4), 0);
        }
        long elapsed = System.nanoTime() - start;

        double nsPerTranslate = (double) elapsed / iterations;
        System.out.printf("translate() performance: %.1f ns/call (%d fast-path hits out of %d)\n",
            nsPerTranslate, tlb.getFastHits(), iterations);

        // Should be very fast — under 100ns per call
        assertTrue(nsPerTranslate < 500, "translate() too slow: " + nsPerTranslate + " ns/call");
        // Nearly all should be fast-path hits (same megapage)
        assertTrue(tlb.getFastHits() >= iterations - 256,
            "Expected mostly fast-path hits, got " + tlb.getFastHits());
    }
}
