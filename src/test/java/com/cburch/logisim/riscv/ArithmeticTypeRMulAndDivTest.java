package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.cburch.logisim.riscv.rv32imDataTest.verifyRegisterRange;

public class ArithmeticTypeRMulAndDivTest {

    rv32imData cpu;
    private long[] expected;

    private static final long x1 = ArithmeticTypeRTest.SIGNED32_MAX;
    private static final long x2 = ArithmeticTypeRTest.SIGNED32_MIN;
    private static final long x3 = ArithmeticTypeRTest.UNSIGNED32_MAX;
    private static final long x4 = 16;
    private static final long x5 = -16;
    private static final long x6 = -100;
    private static final long x7 = -50;
    private static final long x8 = 1;
    private static final long x9 = -1;


    @BeforeEach
    void setup(){
        //create new CPU state
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, rv32imData.CPUState.RUNNING, null);
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
        //set expected to 0
        expected = new long[32];
        for(int i = 0; i < 32; i++) expected[i] = 0;
    }

    @Test
    void instructionTest_mul(){
        // perform mul x20,x1,x8 (positive x positive)
        cpu.update(0x02808a33,0,0, 0);
        // perform mul x21,x4,x5 (positive x negative)
        cpu.update(0x02520ab3,0,0, 0);
        // perform mul x22,x2,x9 (negative x negative)
        cpu.update(0x02910b33,0,0, 0);
        // perform mul x23,x3,x0 (zero)
        cpu.update(0x02018bb3,0,0, 0);
        expected[20] = ArithmeticTypeRTest.SIGNED32_MAX;
        expected[21] = x4*x5;
        expected[22] = x2;
        expected[23] = 0;
        verifyRegisterRange(cpu, expected, 20, 23);
    }

    @Test
    void instructionTest_mulh(){
        // perform mulh x20,x1,x4 (positive x positive)
        cpu.update(0x02409a33,0,0, 0);
        // perform mulh x21,x1,x5 (positive x negative)
        cpu.update(0x02509ab3,0,0, 0);
        // perform mulh x22,x2,x5 (negative x negative)
        cpu.update(0x02511b33,0,0, 0);
        // perform mulh x23,x4,x0 (zero)
        cpu.update(0x02019bb3,0,0, 0);
        // perform mulh x24,x4,x4 (zero)
        cpu.update(0x02421c33,0,0, 0);
        expected[20] = 7;
        expected[21] = -8;
        expected[22] = 8;
        expected[23] = 0;
        expected[24] = 0;
        verifyRegisterRange(cpu, expected, 20, 24);
    }

    @Test
    void instructionTest_mulhsu(){
        // perform mulhsu x20,x1,x4 (positive x positive)
        cpu.update(0x0240aa33,0,0, 0);
        // perform mulhsu x21,x1,x5 (positive x negative)
        cpu.update(0x0250aab3,0,0, 0);
        // perform mulhsu x22,x2,x5 (negative x negative)
        cpu.update(0x02512b33,0,0, 0);
        // perform mulhsu x23,x4,x0 (zero)
        cpu.update(0x0201abb3,0,0, 0);
        // perform mulhsu x24,x4,x4 (zero)
        cpu.update(0x02422c33,0,0, 0);
        expected[20] = 7;
        expected[21] = 2147483639;
        expected[22] = -2147483640;
        expected[23] = 0;
        expected[24] = 0;
        verifyRegisterRange(cpu, expected, 20, 24);
    }

    @Test
    void instructionTest_div(){
        // perform div x20,x1,x4 (positive / positive)
        cpu.update(0x0240ca33,0,0, 0);
        // perform div x21,x1,x5 (positive / negative)
        cpu.update(0x0250cab3,0,0, 0);
        // perform div x22,x2,x5 (negative / negative)
        cpu.update(0x02514b33,0,0, 0);
        // perform div x23,x4,x0 (division by zero)
        cpu.update(0x0201cbb3,0,0, 0);
        // perform div x24,x2,x9 (signed overflow)
        cpu.update(0x02914c33,0,0, 0);
        expected[20] = 134217727;
        expected[21] = -134217727;
        expected[22] = 134217728;
        expected[23] = -1;
        expected[24] = x2;
        verifyRegisterRange(cpu, expected, 20, 24);
    }

    @Test
    void instructionTest_divu(){
        // perform divu x20,x1,x4
        cpu.update(0x0240da33,0,0, 0);
        // perform divu x21,x4,x4
        cpu.update(0x0221dab3,0,0, 0);
        // perform divu x22,x2,x5
        cpu.update(0x02515b33,0,0, 0);
        // perform divu x23,x2,x4
        cpu.update(0x02415bb3,0,0, 0);
        // perform divu x24,x4,x0 (division by zero)
        cpu.update(0x0201dc33,0,0, 0);
        expected[20] = 134217727;
        expected[21] = 1;
        expected[22] = 0;
        expected[23] = 134217728;
        expected[24] = -1;
        verifyRegisterRange(cpu, expected, 20, 24);
    }

    @Test
    void instructionTest_rem(){
        // perform rem x20,x1,x4 (positive / positive)
        cpu.update(0x0240ea33,0,0, 0);
        // perform rem x21,x1,x5 (positive / negative)
        cpu.update(0x0250eab3,0,0, 0);
        // perform rem x22,x6,x7 (negative / negative)
        cpu.update(0x02736b33,0,0, 0);
        // perform rem x23,x4,x0 (division by zero)
        cpu.update(0x02026bb3,0,0, 0);
        // perform rem x24,x2,x9 (signed overflow)
        cpu.update(0x02916c33,0,0, 0);
        expected[20] = 15;
        expected[21] = 15;
        expected[22] = 0;
        expected[23] = x4;
        expected[24] = 0;
        verifyRegisterRange(cpu, expected, 20, 24);
    }

    @Test
    void instructionTest_remu(){
        // perform remu x20,x1,x4
        cpu.update(0x0240ea33,0,0, 0);
        // perform remu x21,x3,x2
        cpu.update(0x0221fab3,0,0, 0);
        // perform remu x22,x6,x6
        cpu.update(0x02736b33,0,0, 0);
        // perform remu x23,x4,x0 (division by zero)
        cpu.update(0x02026bb3,0,0, 0);
        expected[20] = 15;
        expected[21] = 2147483647;
        expected[22] = 0;
        expected[23] = x4;
        verifyRegisterRange(cpu, expected, 20, 24);
    }
}
