package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BranchingTest {
    @Test
    void instructionTest_beq() {
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        long[] expected = new long[32];

        // Test 1: beq x6, x0, 0x00000004 (forward jump)
        // beq x6, x0, 0x00000004
        cpu.update(0x00030263,0,0, 0);
        assertEquals(0x400004, cpu.getPC().get());

        // Test 2: beq x5, x0, 0xfffffffc (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        // beq x5, x0, 0xfffffffc
        cpu.update(0xfe028ee3L,0,0, 0);
        assertEquals(0x3ffffc, cpu.getPC().get());

        // Test 3: beqz x0, 0xfffffff0 (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        // beqz x0, 0xfffffff0
        cpu.update(0xfe00083,0,0, 0);
        assertEquals(0x400000, cpu.getPC().get());
    }

    @Test
    void instructionTest_bne() {
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        long[] expected = new long[32];
        cpu.setX(5, 10);
        cpu.setX(6, 20);

        // Test 1: bne x5, x6, 0x00000004 (forward jump)
        // bne x5, x6, 0x00000004
        cpu.update(0x00430363,0,0, 0);
        assertEquals(0x400004, cpu.getPC().get());

        // Test 2: bne x5, x6, 0xfffffffc (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        cpu.setX(5, 10);
        cpu.setX(6, 10);
        // bne x5, x6, 0xfffffffc
        cpu.update(0xffe30363,0,0, 0);
        assertEquals(0x400004, cpu.getPC().get());
    }

    @Test
    void instructionTest_bge() {
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        long[] expected = new long[32];
        cpu.setX(5, 10);
        cpu.setX(6, 20);

        // Test 1: bge x6, x5, 0x00000004 (forward jump)
        // bge x6, x5, 0x00000004
        cpu.update(0x00428363,0,0, 0);
        assertEquals(0x400004, cpu.getPC().get());

        // Test 2: bge x5, x6, 0xfffffffc (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        cpu.setX(5, 20);
        cpu.setX(6, 10);
        // bge x5, x6, 0xfffffffc
        cpu.update(0xffe28263L,0,0, 0);
        assertEquals(0x400004, cpu.getPC().get());
    }

    @Test
    void instructionTest_bltu() {
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        long[] expected = new long[32];
        cpu.setX(5, 10);
        cpu.setX(6, 20);

        // Test 1: bltu x5, x6, 0x00000004 (forward jump)
        // bltu x5, x6, 0x00000004
        cpu.update(0x00430303,0,0, 0);
        assertEquals(0x400000, cpu.getPC().get());

        // Test 2: bltu x6, x5, 0xfffffffc (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        cpu.setX(5, 20);
        cpu.setX(6, 10);
        // bltu x6, x5, 0xfffffffc
        cpu.update(0xffe28303,0,0, 0);
        assertEquals(0x400000, cpu.getPC().get());
    }

    @Test
    void instructionTest_bgeu() {
        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        long[] expected = new long[32];
        cpu.setX(5, 20);
        cpu.setX(6, 10);

        // Test 1: bgeu x5, x6, 0x00000004 (forward jump)
        // bgeu x5, x6, 0x00000004
        cpu.update(0x00430383,0,0, 0);
        assertEquals(0x400000, cpu.getPC().get());

        // Test 2: bgeu x6, x5, 0xfffffffc (backward jump)
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        cpu.setX(5, 10);
        cpu.setX(6, 20);
        // bgeu x6, x5, 0xfffffffc
        cpu.update(0xffe28383L,0,0, 0);
        assertEquals(0x400000, cpu.getPC().get());
    }
}
