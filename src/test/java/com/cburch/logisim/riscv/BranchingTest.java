package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BranchingTest {
    @Test
    void instructionTest_beq() {
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
        long[] expected = new long[32];

        // Test 1: beq x6, x0, 0x00000004 (forward jump)
        cpu.update(0x00030263);  // beq x6, x0, 0x00000004
        assertEquals(0x400004, cpu.getPC().get());

        // Test 2: beq x5, x0, 0xfffffffc (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000);
        cpu.update(0xfe028ee3L);  // beq x5, x0, 0xfffffffc
        assertEquals(0x3ffffc, cpu.getPC().get());

        // Test 3: beqz x0, 0xfffffff0 (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000);
        cpu.update(0xfe00083);  // beqz x0, 0xfffffff0
        assertEquals(0x400000, cpu.getPC().get());
    }

    @Test
    void instructionTest_bne() {
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
        long[] expected = new long[32];
        cpu.setX(5, 10);
        cpu.setX(6, 20);

        // Test 1: bne x5, x6, 0x00000004 (forward jump)
        cpu.update(0x00430363);  // bne x5, x6, 0x00000004
        assertEquals(0x400004, cpu.getPC().get());

        // Test 2: bne x5, x6, 0xfffffffc (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000);
        cpu.setX(5, 10);
        cpu.setX(6, 10);
        cpu.update(0xffe30363);  // bne x5, x6, 0xfffffffc
        assertEquals(0x400004, cpu.getPC().get());
    }

    @Test
    void instructionTest_bge() {
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
        long[] expected = new long[32];
        cpu.setX(5, 10);
        cpu.setX(6, 20);

        // Test 1: bge x6, x5, 0x00000004 (forward jump)
        cpu.update(0x00428363);  // bge x6, x5, 0x00000004
        assertEquals(0x400004, cpu.getPC().get());

        // Test 2: bge x5, x6, 0xfffffffc (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000);
        cpu.setX(5, 20);
        cpu.setX(6, 10);
        cpu.update(0xffe28263L);  // bge x5, x6, 0xfffffffc
        assertEquals(0x400004, cpu.getPC().get());
    }

    @Test
    void instructionTest_bltu() {
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
        long[] expected = new long[32];
        cpu.setX(5, 10);
        cpu.setX(6, 20);

        // Test 1: bltu x5, x6, 0x00000004 (forward jump)
        cpu.update(0x00430303);  // bltu x5, x6, 0x00000004
        assertEquals(0x400000, cpu.getPC().get());

        // Test 2: bltu x6, x5, 0xfffffffc (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000);
        cpu.setX(5, 20);
        cpu.setX(6, 10);
        cpu.update(0xffe28303);  // bltu x6, x5, 0xfffffffc
        assertEquals(0x400000, cpu.getPC().get());
    }

    @Test
    void instructionTest_bgeu() {
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
        long[] expected = new long[32];
        cpu.setX(5, 20);
        cpu.setX(6, 10);

        // Test 1: bgeu x5, x6, 0x00000004 (forward jump)
        cpu.update(0x00430383);  // bgeu x5, x6, 0x00000004
        assertEquals(0x400000, cpu.getPC().get());

        // Test 2: bgeu x6, x5, 0xfffffffc (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000);
        cpu.setX(5, 10);
        cpu.setX(6, 20);
        cpu.update(0xffe28383L);  // bgeu x6, x5, 0xfffffffc
        assertEquals(0x400000, cpu.getPC().get());
    }
}
