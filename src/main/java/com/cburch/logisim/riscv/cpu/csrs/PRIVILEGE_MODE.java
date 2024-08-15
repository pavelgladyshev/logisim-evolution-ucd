package com.cburch.logisim.riscv.cpu.csrs;

public enum PRIVILEGE_MODE {
    MACHINE(0b11),
    SUPERVISOR(0b01),
    USER(0b00);

    private final int value;

    PRIVILEGE_MODE(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

