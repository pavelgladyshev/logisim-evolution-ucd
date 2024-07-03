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
        void instructionTest_addi() {

            out.println("rv32imData test: addi x5,x0,12 should load 12 into x5");
            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.update(0x00c00293);  // perform addi x5,x0,12
            out.printf("\tTesting CPU state after addi x5,x0,12 ...\n");
            assertEquals(0x400004, cpu.getPC().get());
            long[] expected = new long[32];
            expected[5] = 12;
            assertRegistersEqual(cpu, expected);

            out.println("rv32imData test: addi x5,x0,-1 should load -1 into x5");
            cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.update(0xfff00293);  // perform addi x5,x0,-1
            out.printf("\tTesting CPU state after addi x5,x0,-1 ...\n");
            assertEquals(0x400004, cpu.getPC().get());
            expected = new long[32];
            expected[5] = -1;
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
            expected[6] = 123 << 5;
            assertRegistersEqual(cpu, expected);
        }

        @Test
        void instructionTest_slti() {
            out.println("rv32imData test: slti x6,x5,10 should set x6 to 1 if x5 is less than 10");

            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.setX(5, 5);   // Initially, x5 = 5
            cpu.update(0x00a2b313);  // perform slti x6,x5,10
            out.printf("\tTesting CPU state after slti x6,x5,10...\n");
            assertEquals(0x400004, cpu.getPC().get());
            long[] expected = new long[32];
            expected[5] = 5;
            expected[6] = 1;  // x5 < 10
            assertRegistersEqual(cpu, expected);

            out.println("rv32imData test: slti x6,x5,5 should set x6 to 0 if x5 is not less than 5");
            cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.setX(5, 5);   // Initially, x5 = 5
            cpu.update(0x0052b313);  // perform slti x6,x5,5
            out.printf("\tTesting CPU state after slti x6,x5,5...\n");
            assertEquals(0x400004, cpu.getPC().get());
            expected = new long[32];
            expected[5] = 5;
            expected[6] = 0;  // x5 >= 5
            assertRegistersEqual(cpu, expected);
        }

        @Test
        void instructionTest_sltiu() {
            out.println("rv32imData test: sltiu x6,x5,10 should set x6 to 1 if x5 is less than 10 (unsigned comparison)");

            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.setX(5, 5);   // Initially, x5 = 5
            cpu.update(0x00a2b313);  // perform sltiu x6,x5,10
            out.printf("\tTesting CPU state after sltiu x6,x5,10...\n");
            assertEquals(0x400004, cpu.getPC().get());
            long[] expected = new long[32];
            expected[5] = 5;
            expected[6] = 1;  // x5 <u 10
            assertRegistersEqual(cpu, expected);

            out.println("rv32imData test: sltiu x6,x5,5 should set x6 to 0 if x5 is not less than 5 (unsigned comparison)");
            cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.setX(5, 5);   // Initially, x5 = 5
            cpu.update(0x0052b313);  // perform sltiu x6,x5,5
            out.printf("\tTesting CPU state after sltiu x6,x5,5...\n");
            assertEquals(0x400004, cpu.getPC().get());
            expected = new long[32];
            expected[5] = 5;
            expected[6] = 0;  // x5 is not less thanu 5
            assertRegistersEqual(cpu, expected);
        }

        @Test
        void instructionTest_xori() {
            out.println("rv32imData test: xori x6,x5,0xff should perform bitwise xor of x5 and 0xff and store in x6");

            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.setX(5, 0xff00ff00L);   // Initially, x5 = 0xff00ff00
            cpu.update(0x0ff2c313);  // perform xori x6,x5,0xff
            out.printf("\tTesting CPU state after xori x6,x5,0xff...\n");
            assertEquals(0x400004, cpu.getPC().get());
            long[] expected = new long[32];
            expected[5] = 0xff00ff00L;
            expected[6] = 0xff00ffffL;
            assertRegistersEqual(cpu, expected);
        }

        @Test
        void instructionTest_srli() {
            out.println("rv32imData test: srli x6,x5,2 should shift right logical x5 by 2 and store in x6");

            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.setX(5, 123);   // Initially, x5 = 123
            cpu.update(0x0022d313);  // perform srli x6,x5,2
            out.printf("\tTesting CPU state after srli x6,x5,2...\n");
            assertEquals(0x400004, cpu.getPC().get());
            long[] expected = new long[32];
            expected[5] = 123;
            expected[6] = 123 >>> 2;
            assertRegistersEqual(cpu, expected);
        }

        @Test
        void instructionTest_srai() {
            out.println("rv32imData test: srai x6,x5,2 should shift right arithmetic x5 by 2 and store in x6");

            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.setX(5, -123);   // Initially, x5 = -123
            cpu.update(0x4022d313);  // perform srai x6,x5,2
            out.printf("\tTesting CPU state after srai x6,x5,2...\n");
            assertEquals(0x400004, cpu.getPC().get());
            long[] expected = new long[32];
            expected[5] = -123;
            expected[6] = -123 >> 2;
            assertRegistersEqual(cpu, expected);
        }

        @Test
        void instructionTest_ori() {
            out.println("rv32imData test: ori x6,x5,0xff should perform bitwise or of x5 and 0xff and store in x6");

            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.setX(5, 0xff00ff00L);   // Initially, x5 = 0xff00ff00
            cpu.update(0x0ff2e313);  // perform ori x6,x5,0xff
            out.printf("\tTesting CPU state after ori x6,x5,0xff...\n");
            assertEquals(0x400004, cpu.getPC().get());
            long[] expected = new long[32];
            expected[5] = 0xff00ff00L;
            expected[6] = 0xff00ffffL;
            assertRegistersEqual(cpu, expected);
        }

        @Test
        void instructionTest_andi() {
            out.println("rv32imData test: andi x6,x5,0xff should perform bitwise and of x5 and 0xff and store in x6");

            rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
            cpu.setX(5, 0xff00ff00L);   // Initially, x5 = 0xff00ff00
            cpu.update(0x0ff2f313);  // perform andi x6,x5,0xff
            out.printf("\tTesting CPU state after andi x6,x5,0xff...\n");
            assertEquals(0x400004, cpu.getPC().get());
            long[] expected = new long[32];
            expected[5] = 0xff00ff00L;
            expected[6] = 0x00;
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
            assertEquals(0x3ffffc, cpu.getPC().get());
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

    /** Verify values of all registers from startIndex to endIndex against expected.*/
    public static void verifyRegisterRange(rv32imData cpu, long[] expected, int startIndex, int endIndex) {
        switch(startIndex) {
            case 0:
                assertEquals(expected[0], (cpu.getX(0)));
                if(endIndex == 0) break;
            case 1:
                assertEquals(expected[1], (cpu.getX(1)));
                if(endIndex == 1) break;
            case 2:
                assertEquals(expected[2], (cpu.getX(2)));
                if(endIndex == 2) break;
            case 3:
                assertEquals(expected[3], (cpu.getX(3)));
                if(endIndex == 3) break;
            case 4:
                assertEquals(expected[4], (cpu.getX(4)));
                if(endIndex == 4) break;
            case 5:
                assertEquals(expected[5], (cpu.getX(5)));
                if(endIndex == 5) break;
            case 6:
                assertEquals(expected[6], (cpu.getX(6)));
                if(endIndex == 6) break;
            case 7:
                assertEquals(expected[7], (cpu.getX(7)));
                if(endIndex == 7) break;
            case 8:
                assertEquals(expected[8], (cpu.getX(8)));
                if(endIndex == 8) break;
            case 9:
                assertEquals(expected[9], (cpu.getX(9)));
                if(endIndex == 9) break;
            case 10:
                assertEquals(expected[10], (cpu.getX(10)));
                if(endIndex == 10) break;
            case 11:
                assertEquals(expected[11], (cpu.getX(11)));
                if(endIndex == 11) break;
            case 12:
                assertEquals(expected[12], (cpu.getX(12)));
                if(endIndex == 12) break;
            case 13:
                assertEquals(expected[13], (cpu.getX(13)));
                if(endIndex == 13) break;
            case 14:
                assertEquals(expected[14], (cpu.getX(14)));
                if(endIndex == 14) break;
            case 15:
                assertEquals(expected[15], (cpu.getX(15)));
                if(endIndex == 15) break;
            case 16:
                assertEquals(expected[16], (cpu.getX(16)));
                if(endIndex == 16) break;
            case 17:
                assertEquals(expected[17], (cpu.getX(17)));
                if(endIndex == 17) break;
            case 18:
                assertEquals(expected[18], (cpu.getX(18)));
                if(endIndex == 18) break;
            case 19:
                assertEquals(expected[19], (cpu.getX(19)));
                if(endIndex == 19) break;
            case 20:
                assertEquals(expected[20], (cpu.getX(20)));
                if(endIndex == 20) break;
            case 21:
                assertEquals(expected[21], (cpu.getX(21)));
                if(endIndex == 21) break;
            case 22:
                assertEquals(expected[22], (cpu.getX(22)));
                if(endIndex == 22) break;
            case 23:
                assertEquals(expected[23], (cpu.getX(23)));
                if(endIndex == 23) break;
            case 24:
                assertEquals(expected[24], (cpu.getX(24)));
                if(endIndex == 24) break;
            case 25:
                assertEquals(expected[25], (cpu.getX(25)));
                if(endIndex == 25) break;
            case 26:
                assertEquals(expected[26], (cpu.getX(26)));
                if(endIndex == 26) break;
            case 27:
                assertEquals(expected[27], (cpu.getX(27)));
                if(endIndex == 27) break;
            case 28:
                assertEquals(expected[28], (cpu.getX(28)));
                if(endIndex == 28) break;
            case 29:
                assertEquals(expected[29], (cpu.getX(29)));
                if(endIndex == 29) break;
            case 30:
                assertEquals(expected[30], (cpu.getX(30)));
                if(endIndex == 30) break;
            case 31:
                assertEquals(expected[31], (cpu.getX(31)));
                if(endIndex == 31) break;
        }
    }
}