package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.Test;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.cburch.logisim.riscv.rv32imDataTest.assertRegistersEqual;

public class ArithmeticTypeITest {
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
