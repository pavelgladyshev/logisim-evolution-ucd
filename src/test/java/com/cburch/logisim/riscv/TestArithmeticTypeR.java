package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestArithmeticTypeR {

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
