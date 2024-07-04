package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import org.junit.jupiter.api.Test;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.cburch.logisim.riscv.rv32imConstructorTest.assertRegistersEqual;

public class ArithmeticTypeUTest {
    @Test
    void instructionTest_lui() {
        out.println("rv32imData test: lui x5,0x12345 should load 0x12345000 into x5");

        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
        cpu.update(0x123452b7);  // perform lui x5,0x12345
        out.printf("\tTesting CPU state after lui x5,0x12345 ...\n");
        assertEquals(0x400004, cpu.getPC().get());
        long[] expected = new long[32];
        expected[5] = 0x12345000L;
        assertRegistersEqual(cpu, expected);

        out.println("rv32imData test: lui x6,0x0 should load 0x0 into x6");
        cpu = new rv32imData(Value.FALSE, 0x400000);
        cpu.update(0x000002b7);  // perform lui x6,0x0
        out.printf("\tTesting CPU state after lui x6,0x0 ...\n");
        assertEquals(0x400004, cpu.getPC().get());
        expected = new long[32];
        expected[6] = 0x0;
        assertRegistersEqual(cpu, expected);
    }

    @Test
    void instructionTest_auipc() {
        out.println("rv32imData test: auipc x5,0x12345 should add 0x12345000 to PC and store in x5");

        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
        cpu.update(0x12345297);  // perform auipc x5,0x12345
        out.printf("\tTesting CPU state after auipc x5,0x12345 ...\n");
        assertEquals(0x400004, cpu.getPC().get());
        long[] expected = new long[32];
        expected[5] = 0x12745000L;
        assertRegistersEqual(cpu, expected);

        out.println("rv32imData test: auipc x6,0x0 should load current PC into x6");
        cpu = new rv32imData(Value.FALSE, 0x400000);
        cpu.update(0x00000317);  // perform auipc x6,0x0
        out.printf("\tTesting CPU state after auipc x6,0x0 ...\n");
        assertEquals(0x400004, cpu.getPC().get());
        expected = new long[32];
        expected[6] = 0x400000;
        assertRegistersEqual(cpu, expected);
    }
}
