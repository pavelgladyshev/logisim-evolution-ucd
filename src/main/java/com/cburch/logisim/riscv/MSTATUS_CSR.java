package com.cburch.logisim.riscv;

public class MSTATUS_CSR extends CSR_RW {

    protected BITFIELD MIE;
    protected BITFIELD MPIE;
    protected BITFIELD MPP;

    MSTATUS_CSR(long initValue) {
        super(initValue);
        MIE = new BITFIELD(this, 3,3);
        MPIE = new BITFIELD(this, 7,7);
        MPP = new MPP(this, 11,12);

        MPP.set(PRIVILEGE_MODE.MACHINE.getValue());
    }
    
    public class MPP extends BITFIELD {
        MPP(CSR register, int startBitInclusive, int endBitInclusive) {
            super(register, startBitInclusive, endBitInclusive);
        }
        public PRIVILEGE_MODE getLastPrivilegeMode() {
            return switch ((int) get()) {
                case 0b11 -> PRIVILEGE_MODE.MACHINE;
                case 0b01 -> PRIVILEGE_MODE.SUPERVISOR;
                case 0b00 -> PRIVILEGE_MODE.USER;
                default -> throw new IllegalStateException("Unexpected value: " + (int) get());
            };
        }
    }

    @Override
    protected void write(long value) {
        super.write(value);
    }
}
