package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StoreDataCacheTest {

    private rv32imData cpu;

    @BeforeEach
    void setUp() {
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
    }

    @Test
    void testCacheUpdateOnStoreInstruction_1Byte() {
        long storeAddress = 0x1000;
        long storeValue = 0x12;

        cpu.cache.update(storeAddress, storeValue);
        assertTrue(cpu.cache.isValid(storeAddress));
        assertEquals(storeValue, cpu.cache.get(storeAddress));
    }

    @Test
    void testCacheUpdateOnStoreInstruction_2Bytes() {
        long storeAddress = 0x1000;
        long storeValue = 0x1234;

        cpu.cache.update(storeAddress, storeValue);
        assertTrue(cpu.cache.isValid(storeAddress));
        assertEquals(storeValue, cpu.cache.get(storeAddress));
    }

    @Test
    void testCacheUpdateOnStoreInstruction_4Bytes() {
        long storeAddress = 0x1000;
        long storeValue = 0x12345678;

        cpu.cache.update(storeAddress, storeValue);
        assertTrue(cpu.cache.isValid(storeAddress));
        assertEquals(storeValue, cpu.cache.get(storeAddress));
    }

    @Test
    void testCacheUpdateAfterMultipleStores() {
        long storeAddress1 = 0x1000;
        long storeValue1 = 0x12345678;
        cpu.cache.update(storeAddress1, storeValue1);

        long storeAddress2 = 0x1004;
        long storeValue2 = 0x87654321;
        cpu.cache.update(storeAddress2, storeValue2);

        assertTrue(cpu.cache.isValid(storeAddress1));
        assertTrue(cpu.cache.isValid(storeAddress2));
        assertEquals(storeValue1, cpu.cache.get(storeAddress1));
        assertEquals(storeValue2, cpu.cache.get(storeAddress2));
    }

    @Test
    void testCacheMissOnInstructionFetch() {
        long pcVal = 0x2000;
        cpu.getPC().set(pcVal);

        assertFalse(cpu.cache.isValid(pcVal));
    }

    @Test
    void testCacheHitOnInstructionFetch() {
        long pcVal = 0x2000;
        cpu.cache.update(pcVal, 0xdeadbeef);
        cpu.getPC().set(pcVal);
        cpu.fetchNextInstruction();
        assertTrue(cpu.cache.isValid(pcVal));
        assertEquals(0xdeadbeef, cpu.cache.get(pcVal));
    }


}
