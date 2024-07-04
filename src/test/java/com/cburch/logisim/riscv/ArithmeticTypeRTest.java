package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.cburch.logisim.riscv.rv32imDataTest.verifyRegisterRange;

public class ArithmeticTypeRTest {

    rv32imData cpu;

    public static final long SIGNED32_MAX = 2_147_483_647L;
    public static final long SIGNED32_MIN = -2_147_483_648L;
    public static final long UNSIGNED32_MAX = 4_294_967_295L;

    private static final long x1 = SIGNED32_MAX;
    private static final long x2 = SIGNED32_MIN;
    private static final long x3 = UNSIGNED32_MAX;
    private static final long x4 = 100;
    private static final long x5 = 33;
    private static final long x6 = -100;
    private static final long x7 = -50;
    private static final long x8 = 1;
    private static final long x9 = -1;
    private static final long x10 = 0xff00ff00L;
    private static final long x11 = 0x00ff00ffL;
    private static final long x12 = 31;

    private long[] expected;

    @BeforeEach
    void setup(){
        //create new CPU state
        cpu = new rv32imData(Value.FALSE, 0x400000);
        //set registers
        cpu.setX(1, x1);
        cpu.setX(2, x2);
        cpu.setX(3, x3);
        cpu.setX(4, x4);
        cpu.setX(5, x5);
        cpu.setX(6, x6);
        cpu.setX(7, x7);
        cpu.setX(8, x8);
        cpu.setX(9, x9);
        cpu.setX(10,x10);
        cpu.setX(11,x11);
        cpu.setX(12,x12);
        //set expected to 0
        expected = new long[32];
        for(int i = 0; i < 32; i++) expected[i] = 0;
    }
    @Test
    void instructionTest_add_no_overflow() {
        cpu.update(0X00520A33);  // perform add x20,x4,x5 (positive + positive)
        cpu.update(0X00730AB3);  // perform add x21,x6,x7 (negative + negative)
        cpu.update(0X00720B33);  // perform add x22,x4,x7 (positive + negative)
        cpu.update(0X00538BB3);  // perform add x23,x7,x5 (negative + positive)
        expected[20] = x4 + x5;
        expected[21] = x6 + x7;
        expected[22] = x4 + x7;
        expected[23] = x7 + x5;
        verifyRegisterRange(cpu, expected, 20,23);
    }

    @Test
    void instructionTest_add_overflow() {
        cpu.update(0X00808A33);  // perform add x20,x1,x8 (positive + positive)
        cpu.update(0X00910AB3);  // perform add x21,x2,x9 (negative + negative)
        expected[20] = SIGNED32_MIN;
        expected[21] = SIGNED32_MAX;
        verifyRegisterRange(cpu, expected, 20,21);
    }

    @Test
    void instructionTest_sub_no_overflow() {
        cpu.update(0X40520A33);  // perform sub x20,x4,x5 (positive - positive = positive)
        cpu.update(0X40638AB3);  // perform sub x21,x7,x6 (negative - negative = positive)
        cpu.update(0X40720B33);  // perform sub x22,x4,x7 (positive - negative = positive)
        cpu.update(0X40428BB3);  // perform sub x23,x5,x4 (positive - positive = negative)
        cpu.update(0X40730C33);  // perform sub x24,x6,x7 (negative - negative = negative)
        cpu.update(0X40530CB3);  // perform sub x25,x6,x5 (negative - positive = negative)
        expected[20] = x4 - x5;
        expected[21] = x7 - x6;
        expected[22] = x4 - x7;
        expected[23] = x5 - x4;
        expected[24] = x6 - x7;
        expected[25] = x6 - x5;
        verifyRegisterRange(cpu, expected, 20,25);
    }

    @Test
    void instructionTest_sub_overflow() {
        cpu.update(0X40908A33);  // perform sub x20,x1,x9 (positive - negative)
        cpu.update(0X40810AB3);  // perform sub x21,x2,x8 (negative - positive)
        expected[20] = SIGNED32_MIN;
        expected[21] = SIGNED32_MAX;
        verifyRegisterRange(cpu, expected, 20,21);
    }

    @Test
    void instructionTest_slt() {
        cpu.update(0X0083AA33);  // perform slt x20,x7,x8 (rs1 < rs2)
        cpu.update(0X0020AAB3);  // perform slt x21,x1,x2 (rs1 > rs2)
        cpu.update(0X00002B33);  // perform slt x22,x0,x0 (rs1 = rs2)
        expected[20] = 1;
        expected[21] = 0;
        expected[22] = 0;
        verifyRegisterRange(cpu, expected, 20,22);
    }

    @Test
    void instructionTest_sltu() {
        cpu.update(0X0083AA33);  // perform sltu x20,x0,x8 (rs1 < rs2)
        cpu.update(0X0020AAB3);  // perform sltu x21,x8,x0 (rs1 > rs2)
        cpu.update(0X00002B33);  // perform sltu x22,x0,x0 (rs1 = 0, rs2 = 0)
        cpu.update(0X00603BB3);  // perform sltu x23,x0,x6 (rs1 = 0, rs2 != 0)
        expected[20] = 1;
        expected[21] = 0;
        expected[22] = 0;
        expected[23] = 1;
        verifyRegisterRange(cpu, expected, 20,23);
    }

    @Test
    void instructionTest_and() {
        cpu.update(0X00B57A33); // perform and x20,x10,x11
        cpu.update(0X00A57AB3); // perform and x21,x10,x10
        expected[20] = 0x0;
        expected[21] = x10;
        verifyRegisterRange(cpu, expected, 20,21);
    }

    @Test
    void instructionTest_or() {
        cpu.update(0X00B56A33); // perform or x20,x10,x11
        cpu.update(0X00A56AB3); // perform or x21,x10,x10
        expected[20] = 0xffffffffL;
        expected[21] = x10;
        verifyRegisterRange(cpu, expected, 20,21);
    }

    @Test
    void instructionTest_xor() {
        cpu.update(0X00B54A33); // perform xor x20,x10,x11
        cpu.update(0X00A54AB3); // perform xor x21,x10,x10
        expected[20] = 0xffffffffL;
        expected[21] = 0x0L;
        verifyRegisterRange(cpu, expected, 20,21);
    }

    @Test
    void instructionTest_sll() {
        cpu.update(0x00c41a33); // perform sll x20,x8,x12
        cpu.update(0x00541ab3); // perform sll x21,x8,x5
        cpu.update(0x00041b33); // perform sll x22,x8,x0
        expected[20] = 0x80000000L;
        expected[21] = 0b10;
        expected[22] = x8;
        verifyRegisterRange(cpu, expected, 20,22);
    }

    @Test
    void instructionTest_srl() {
        cpu.update(0x00c55a33); // perform srl x20,x10,x12
        cpu.update(0x0045dab3); // perform srl x21,x11,x4
        cpu.update(0x0005db33); // perform srl x22,x11,x0
        expected[20] = 0b1;
        expected[21] = 0x000ff00f;
        expected[22] = x11;
        verifyRegisterRange(cpu, expected, 20,22);
    }

    @Test
    void instructionTest_sra() {
        cpu.update(0x40c55a33); // perform sra x20,x10,x12
        cpu.update(0x4045dab3); // perform sra x21,x11,x4
        cpu.update(0x4005db33); // perform sra x22,x11,x0
        expected[20] = -1L;
        expected[21] = 0x000ff00fL;
        expected[22] = x11;
        verifyRegisterRange(cpu, expected, 20,22);
    }

}
