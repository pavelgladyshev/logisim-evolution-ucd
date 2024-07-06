package com.cburch.logisim.riscv;

import com.cburch.logisim.data.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static java.lang.System.out;

public class rv32imDataTest {

    @Test
    void defaultConstructor_shouldProduceZeroedState() {
        out.println("rv32imData test: default constructor should produce zeroed initial state with specified initial PC value");

        rv32imData cpu = new rv32imData(Value.FALSE, 0x400000);
        out.printf("\tTesting newly created CPU for initial values ...\n");

        long[] expected = new long[32];
        assertRegistersEqual(cpu, expected);
    }

    public static void assertRegistersEqual(rv32imData tested, long[] expected) {
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