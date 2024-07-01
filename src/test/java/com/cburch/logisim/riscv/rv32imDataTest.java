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

    private void assertRegistersEqual(rv32imData tested, long[] expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], (tested.getX(i)), "Register x" + i + " did not match expected value!");
        }
    }
}