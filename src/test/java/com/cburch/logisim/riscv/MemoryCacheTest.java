package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.MemoryCache;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.lang.System.out;
import static com.cburch.logisim.riscv.rv32imDataTest.assertRegistersEqual;
import static org.junit.jupiter.api.Assertions.*;

class MemoryCacheTest {
    private MemoryCache cache;

    @BeforeEach
    void setUp() {
        cache = new MemoryCache();
    }

    @Test
    void testInvalidate() {
        long address = 0x00001000L;
        long value = 0xABCDEF01L;

        cache.update(address, value);
        assertTrue(cache.isValid(address));
        assertEquals(value, cache.get(address));

        cache.invalidate();
        assertFalse(cache.isValid(address));
        assertThrows(IllegalStateException.class, () -> cache.get(address));
    }

    @Test
    void testUpdateAndGet32BitAddress() {
        long address = 0x00002000L;
        long value = 0x12345678L;

        cache.update(address, value);
        assertTrue(cache.isValid(address));
        assertEquals(value, cache.get(address));
    }

    @Test
    void testCacheMissThrowsException() {
        long address = 0x00003000L;
        assertFalse(cache.isValid(address));
        assertThrows(IllegalStateException.class, () -> cache.get(address));
    }

    @Test
    void testOverwriteData() {
        long address = 0x00004000L;
        long value1 = 0x11111111L;
        long value2 = 0x22222222L;

        cache.update(address, value1);
        assertEquals(value1, cache.get(address));

        cache.update(address, value2);
        assertEquals(value2, cache.get(address));
    }

    @Test
    void testIndexCollisionEvictsOldTag() {
        long addr1 = 0x00000000L;
        long addr2 = 0x00004000L;

        cache.update(addr1, 0xAAAAAAAAL);
        assertTrue(cache.isValid(addr1));
        assertFalse(cache.isValid(addr2));

        cache.update(addr2, 0xBBBBBBBBL);
        assertTrue(cache.isValid(addr2));
        assertFalse(cache.isValid(addr1));
    }

    @Test
    void testEdgeAddresses() {
        long lowAddr = 0x00000000L;
        long highAddr = 0xFFFFFFFFL & ~0x3L; // highest 32-bit address

        cache.update(lowAddr, 0x1111L);
        cache.update(highAddr, 0xFFFFL);

        assertTrue(cache.isValid(lowAddr));
        assertEquals(0x1111L, cache.get(lowAddr));

        assertTrue(cache.isValid(highAddr));
        assertEquals(0xFFFFL, cache.get(highAddr));
    }

    @Test
    void testFullCacheCoverage() {
        // Fill all 1024 unique cache slots
        for (int i = 0; i < 1024; i++) {
            long addr = ((long) i << 2);
            long data = addr ^ 0x5A5A5A5AL;
            cache.update(addr, data);
        }

        // Verify all entries
        for (int i = 0; i < 1024; i++) {
            long addr = ((long) i << 2);
            long expected = addr ^ 0x5A5A5A5AL;
            assertTrue(cache.isValid(addr));
            assertEquals(expected, cache.get(addr));
        }
    }



    @Test
    public void testBasicCacheStoreAndRetrieve() {
        MemoryCache cache = new MemoryCache();
        long addr = 0x00000004;
        long value = 0x12345678;

        cache.update(addr, value);

        assertTrue(cache.isValid(addr));
        assertEquals(value, cache.get(addr));
    }

    @Test
    public void testInvalidBeforeStore() {
        MemoryCache cache = new MemoryCache();
        long addr = 0x00000008;

        assertFalse(cache.isValid(addr));
        assertThrows(IllegalStateException.class, () -> cache.get(addr));
    }

    @Test
    public void testInvalidateCache() {
        MemoryCache cache = new MemoryCache();
        long addr = 0x0000000C;
        long value = 0xCAFEBABE;

        cache.update(addr, value);
        cache.invalidate();

        assertFalse(cache.isValid(addr));
    }

    @Test
    public void testFullCacheOverwrite() {
        MemoryCache cache = new MemoryCache();

        for (int i = 0; i < 1024; i++) {
            long addr = (long) i << 2;
            long val = i * 123;
            cache.update(addr, val);
            assertTrue(cache.isValid(addr));
            assertEquals(val, cache.get(addr));
        }

        for (int i = 0; i < 1024; i++) {
            long addr = (long) i << 2;
            long newVal = i * 456;
            cache.update(addr, newVal);
            assertEquals(newVal, cache.get(addr));
        }
    }


}