package com.cburch.logisim.riscv.cpu.csrs;

import com.cburch.logisim.riscv.cpu.rv32imData;

public enum SCSR {
    SSTATUS(0x100),
    SIE(0x104),
    STVEC(0x105),
    SCOUNTEREN(0x106),
    SENVCFG(0x10A),
    SSCRATCH(0x140),
    SEPC(0x141),
    SCAUSE(0x142),
    STVAL(0x143),
    SIP(0x144),
    SATP(0x180),
    SCONTEXT(0x5A8);

    private final int address;

    SCSR(int address) {
        this.address = address;
    }

    public int getAddress() {
        return address;
    }

    public static long getValue(rv32imData hartData, SCSR csr) {
        return hartData.getCSRValue(csr.getAddress());
    }
}
