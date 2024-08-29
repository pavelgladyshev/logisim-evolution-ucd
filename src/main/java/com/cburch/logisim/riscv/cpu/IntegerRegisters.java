package com.cburch.logisim.riscv.cpu;

import java.util.Arrays;

public class IntegerRegisters {

    public static final String[] registerABINames = {
            "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2", "s0", "s1", "a0", "a1", "a2", "a3", "a4",
            "a5", "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11", "t3", "t4",
            "t5", "t6"
    };

    long x[] = new long[] {0,0,0,0,0,0,0,0,0,0,
                           0,0,0,0,0,0,0,0,0,0,
                           0,0,0,0,0,0,0,0,0,0,
                           0,0};

    public long get(int index) {
        return x[index];
    }

    public void set(int index, long value) {
        if(index != 0) {
            x[index] = value;
        }
    }


    public String readAllRegisters(int pc) {
        StringBuilder ret = new StringBuilder();
        for(int i = 0; i < x.length; i++) {
            char[] newReg = String.format("%08x", x[i]).toCharArray();
            System.out.println(newReg);
            reverseNibbles(ret, newReg);
        }

        char[] pcStr = String.format("%08x", pc).toCharArray();
        reverseNibbles(ret, pcStr);

        return ret.toString();
    }

    private void reverseNibbles(StringBuilder ret, char[] pcStr) {
        for(int j = 0; j < 8; j += 2) {
            char temp = pcStr[j];
            pcStr[j] = pcStr[j+1];
            pcStr[j+1] = temp;
        }
        String reversedPC = new StringBuilder(new String(pcStr)).reverse().toString();
        ret.append(reversedPC);
    }

}
