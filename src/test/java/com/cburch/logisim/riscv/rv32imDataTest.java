package com.cburch.logisim.riscv;

import com.cburch.logisim.data.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static java.lang.System.out;

public class rv32imDataTest {

    @Test
    void defaultContrustor_shouldProduceZeroedState() {

        out.println("rv32imData test: default constructor should produce zeroed initial state with specified initial PC value");

        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
        out.printf("\tTesting newly created CPU for initial values ...\n");
        assertEquals (0x400000,cpu.getPC().get());
        assertEquals (0,cpu.getX(0));
        assertEquals (0,cpu.getX(1));
        assertEquals (0,cpu.getX(2));
        assertEquals (0,cpu.getX(3));
        assertEquals (0,cpu.getX(4));
        assertEquals (0,cpu.getX(5));
        assertEquals (0,cpu.getX(6));
        assertEquals (0,cpu.getX(7));
        assertEquals (0,cpu.getX(8));
        assertEquals (0,cpu.getX(9));
        assertEquals (0,cpu.getX(10));
        assertEquals (0,cpu.getX(11));
        assertEquals (0,cpu.getX(12));
        assertEquals (0,cpu.getX(13));
        assertEquals (0,cpu.getX(14));
        assertEquals (0,cpu.getX(15));
        assertEquals (0,cpu.getX(16));
        assertEquals (0,cpu.getX(17));
        assertEquals (0,cpu.getX(18));
        assertEquals (0,cpu.getX(19));
        assertEquals (0,cpu.getX(20));
        assertEquals (0,cpu.getX(21));
        assertEquals (0,cpu.getX(22));
        assertEquals (0,cpu.getX(23));
        assertEquals (0,cpu.getX(24));
        assertEquals (0,cpu.getX(25));
        assertEquals (0,cpu.getX(26));
        assertEquals (0,cpu.getX(27));
        assertEquals (0,cpu.getX(28));
        assertEquals (0,cpu.getX(29));
        assertEquals (0,cpu.getX(30));
        assertEquals (0,cpu.getX(31));
    }

    @Test
    void instructionTest_addi_as_li() {

        out.println("rv32imData test: addi x5,x0,12 should load 12 into x5");

        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
        cpu.update(0x00c00293);  // perform addi x5,x0,12
        out.printf("\tTesting CPU state after addi x5,x0,12 ...\n");
        assertEquals (0x400004,cpu.getPC().get());
        assertEquals (0,cpu.getX(0));
        assertEquals (0,cpu.getX(1));
        assertEquals (0,cpu.getX(2));
        assertEquals (0,cpu.getX(3));
        assertEquals (0,cpu.getX(4));
        assertEquals (12,cpu.getX(5));
        assertEquals (0,cpu.getX(6));
        assertEquals (0,cpu.getX(7));
        assertEquals (0,cpu.getX(8));
        assertEquals (0,cpu.getX(9));
        assertEquals (0,cpu.getX(10));
        assertEquals (0,cpu.getX(11));
        assertEquals (0,cpu.getX(12));
        assertEquals (0,cpu.getX(13));
        assertEquals (0,cpu.getX(14));
        assertEquals (0,cpu.getX(15));
        assertEquals (0,cpu.getX(16));
        assertEquals (0,cpu.getX(17));
        assertEquals (0,cpu.getX(18));
        assertEquals (0,cpu.getX(19));
        assertEquals (0,cpu.getX(20));
        assertEquals (0,cpu.getX(21));
        assertEquals (0,cpu.getX(22));
        assertEquals (0,cpu.getX(23));
        assertEquals (0,cpu.getX(24));
        assertEquals (0,cpu.getX(25));
        assertEquals (0,cpu.getX(26));
        assertEquals (0,cpu.getX(27));
        assertEquals (0,cpu.getX(28));
        assertEquals (0,cpu.getX(29));
        assertEquals (0,cpu.getX(30));
        assertEquals (0,cpu.getX(31));
    }
}



