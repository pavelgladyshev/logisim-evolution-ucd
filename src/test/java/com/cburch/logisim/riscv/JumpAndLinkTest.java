package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.rv32imData;
import org.junit.jupiter.api.*;
import static com.cburch.logisim.riscv.rv32imDataTest.verifyRegisterRange;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JumpAndLinkTest {

    rv32imData cpu;
    private long[] expected;

    private final static long reset = 0x400000;
    private final static long x20 = reset;

    @BeforeEach
    void setup() {
        //create new CPU state
        cpu = new rv32imData(Value.FALSE, 0x400000, 1234, false, false, rv32imData.CPUState.RUNNING, null);
        //set registers
        cpu.setX(20, x20);
        //set link
        expected = new long[32];
        expected[1] = cpu.getPC().get() + 4;
    }

    @AfterEach
    void verifyLink(){
        verifyRegisterRange(cpu,expected,1,1);
    }

    @Test
    void instructionTest_jal_in_place() {
        // perform jal x1,0
        cpu.update(0x000000ef,0,0, 0);
        assertEquals(reset, cpu.getPC().get());
    }


    @Test
    void instructionTest_jal_forward() {
        // perform jal x1,1048575
        cpu.update(0x7ffff0ef,0,0, 0);
        assertEquals(reset + 1048574, cpu.getPC().get());
    }

    @Test
    void instructionTest_jal_backward() {
        // perform jal x1,-1048576
        cpu.update(0x800000efL,0,0, 0);
        assertEquals(reset + (-1048576), cpu.getPC().get());
    }

    @Test
    void instructionTest_jalr_alignment() {
        // perform jalr x1,2047(x0)
        cpu.update(0x7ff000e7,0,0, 0);
        assertEquals(2046, cpu.getPC().get());
    }

    @Test
    void instructionTest_jalr_forward() {
        // perform jalr x1,2047(x20)
        cpu.update(0x7ffa00e7,0,0, 0);
        assertEquals(x20 + (2046), cpu.getPC().get());
    }

    @Test
    void instructionTest_jalr_backward() {
        // perform jalr x1, -2048(x20)
        cpu.update(0x800a00e7L,0,0, 0);
        assertEquals(x20 + (-2048), cpu.getPC().get());
    }

}
