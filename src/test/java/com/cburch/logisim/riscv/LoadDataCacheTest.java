package com.cburch.logisim.riscv;

import com.cburch.logisim.riscv.cpu.MemoryCache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;


public class LoadDataCacheTest {
    private MemoryCache cache;

    @BeforeEach
    void setUp() {
        cache = new MemoryCache();
    }

    @Test
    void testLoadInstructionCacheHit() {
        long address = 0x1000;
        long expectedValue = 0x12345678;

        cache.update(address, expectedValue);

        assertTrue(cache.isValid(address));
        assertEquals(expectedValue, cache.get(address));
    }

    @Test
    void testLoadInstructionCacheMiss() {
        long address = 0x1000;

        cache.invalidate();

        assertFalse(cache.isValid(address));
        assertThrows(IllegalStateException.class, () -> cache.get(address));
    }

    @Test
    void testLoadInstructionCacheMissThenHit() {
        long address = 0x1000;
        long expectedValue = 0x12345678;

        cache.invalidate();

        assertFalse(cache.isValid(address));

        cache.update(address, expectedValue);

        assertTrue(cache.isValid(address));
        assertEquals(expectedValue, cache.get(address));

        cache.update(address, expectedValue);

        assertTrue(cache.isValid(address));
        assertEquals(expectedValue, cache.get(address));
    }

}
