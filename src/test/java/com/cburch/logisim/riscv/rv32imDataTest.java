package com.cburch.logisim.riscv;

import com.cburch.logisim.data.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static java.lang.System.out;

public class rv32imDataTest {

    @Nested
    class TestConstructor {
        @Test
        void defaultContrustor_shouldProduceZeroedState() {

            out.println("rv32imData test: default constructor should produce zeroed initial state with specified initial PC value");

            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            out.printf("\tTesting newly created CPU for initial values ...\n");

            long[] expected = new long[32];
            assertRegistersEqual(cpu, expected);
        }
    }

    @Nested
    class TestArithmeticTypeI {
        @Test
        void instructionTest_addi_as_li() {

            out.println("rv32imData test: addi x5,x0,12 should load 12 into x5");

            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.update(0x00c00293);  // perform addi x5,x0,12
            out.printf("\tTesting CPU state after addi x5,x0,12 ...\n");
            assertEquals(0x400004, cpu.getPC().get());
            long[] expected = new long[32];
            expected[5] = 12;
            assertRegistersEqual(cpu, expected);
        }

        @Test
        void instructionTest_slli() {
            out.println("rv32imData test: slli x6,x5,5 should set x6 to shift left logical x5 by 5 and ");

            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.setX(5, 123);   // Initially, x5 = 123
            cpu.update(0x00529313);  // perform addi x5,x0,12
            out.printf("\tTesting CPU state after slli x6,x5,5...\n");
            assertEquals(0x400004, cpu.getPC().get());
            long[] expected = new long[32];
            expected[5] = 123;
            expected[6] = 3936;
            assertRegistersEqual(cpu, expected);
        }
    }

    @Nested
    class TestBranching {

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
            assertEquals(0x400000, cpu.getPC().get()+4);
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
            assertEquals(0x400000, cpu.getPC().get()-4);
        }

        @Test
        void instructionTest_bltu() {
            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            long[] expected = new long[32];
            cpu.setX(5, 10);
            cpu.setX(6, 20);

            // Test 1: bltu x5, x6, 0x00000004 (forward jump)
            cpu.update(0x00430303);  // bltu x5, x6, 0x00000004
            assertEquals(0x400004, cpu.getPC().get()+4);

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
            assertEquals(0x400004, cpu.getPC().get()+4);

            // Test 2: bgeu x6, x5, 0xfffffffc (backward jump)
            cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.setX(5, 10);
            cpu.setX(6, 20);
            cpu.update(0xffe28383L);  // bgeu x6, x5, 0xfffffffc
            assertEquals(0x400000, cpu.getPC().get());
        }
    }


    private void assertRegistersEqual(rv32imData tested, long[] expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], (tested.getX(i)), "Register x" + i + " did not match expected value!");
        }
    }
}