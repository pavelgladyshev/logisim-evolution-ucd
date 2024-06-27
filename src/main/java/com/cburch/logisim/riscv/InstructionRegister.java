package com.cburch.logisim.riscv;

public class InstructionRegister {
    long value;

    public InstructionRegister(long value) {
        set(value);
    }

    public long get() {
        return value;
    }

    public void set(long value) {
        this.value = value;
    }

    // methods to get subfields of the instruction

    // R-type instruction fields
    public int opcode() { return (int) (value & ((1 << 7)-1)); }        // value[6:0]
    public int rd() { return (int) ((value >> 7) & ((1 << 5)-1)); }     // value[11:7]
    public int func3() { return (int) ((value >> 12) & ((1 << 3)-1)); } // value[14:12]
    public int rs1() { return (int) ((value >> 15) & ((1 << 5)-1)); }   // value[19:15]
    public int rs2() { return (int) ((value >> 20) & ((1 << 5)-1)); }   // value[24:20]
    public int func7() { return (int) ((value >> 25) & ((1 << 7)-1)); } // value[31:25]

    // I-type additional fields
    public long imm_I() {  // sign-extended immediate field of I-type instruction
        long res = ((value >> 20) & ((1 << 12) - 1)); // value[31:20], zero extended
        res = (res ^ 0x800) - 0x800; // sign-extend 12 bit integer.
        return res;
    }

    // S-type additional fields
    public long imm_S() { return ((func7() << 5) | func3()); }      // value[31:25].value[11:7]

    // U-type additional fields
    public long imm_U() { return ( (value & 0xfffff000L) ^ 0x80000000L) - 0x80000000L; } // value[31:12], sign extended

    // B-type additional fields
    public long imm_B() {
        long imm10_5 = (value >> 25) & 0x3F; // value[30:25]
        long imm_odd = (value >> 7) & 0x1; // value [7]
        long imm4_1 = (value >> 8) & 0xF; // value[11:8]

        return ( ( ( (imm10_5 << 5) | (imm4_1 << 1) | (imm_odd << 11) ) ) ^ 0x800) - 0x800;
    }

    // J-type additional fields
    public long imm_J() {  // sign-extended immediate field of J-type instruction
        long res = ((value >> 20) & ((1 << 11) - 2)); // value[30:21] (imm[10:1])
        res |= ((value >> 9) & ((1 << 11)));          // value[20] (imm[11])
        res |= (value & 0xff000);                     // value[19:12] (imm[19:12])
        res |= ((value >> 11) & (1 << 20));           // value[31] (imm[20])
        res = (res ^ 0x100000) - 0x100000;            // sign-extend 21-bit integer
        return res;
    }
}
