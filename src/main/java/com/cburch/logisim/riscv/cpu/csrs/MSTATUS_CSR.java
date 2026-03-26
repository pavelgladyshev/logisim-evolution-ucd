package com.cburch.logisim.riscv.cpu.csrs;

public class MSTATUS_CSR extends CSR_RW {

    // Supervisor-mode fields
    public BITFIELD SIE;    // bit 1: Supervisor Interrupt Enable
    public BITFIELD SPIE;   // bit 5: Supervisor Previous Interrupt Enable
    public SPP_FIELD SPP;   // bit 8: Supervisor Previous Privilege (1 bit: 0=User, 1=Supervisor)

    // Machine-mode fields
    public BITFIELD MIE;    // bit 3: Machine Interrupt Enable
    public BITFIELD MPIE;   // bit 7: Machine Previous Interrupt Enable
    public MPP MPP;          // bits 11-12: Machine Previous Privilege

    // Virtual memory control fields
    public BITFIELD MPRV;   // bit 17: Modify PRiVilege (use MPP for load/store translation)
    public BITFIELD SUM;    // bit 18: permit Supervisor User Memory access
    public BITFIELD MXR;    // bit 19: Make eXecutable Readable

    public MSTATUS_CSR(long initValue) {
        super(initValue);
        SIE = new BITFIELD(this, 1, 1);
        MIE = new BITFIELD(this, 3, 3);
        SPIE = new BITFIELD(this, 5, 5);
        MPIE = new BITFIELD(this, 7, 7);
        SPP = new SPP_FIELD(this, 8, 8);
        MPP = new MPP(this, 11, 12);
        MPRV = new BITFIELD(this, 17, 17);
        SUM = new BITFIELD(this, 18, 18);
        MXR = new BITFIELD(this, 19, 19);

        MPP.set(PRIVILEGE_MODE.MACHINE.getValue());
    }

    /**
     * SPP field: 1-bit (0 = User, 1 = Supervisor)
     */
    public static class SPP_FIELD extends BITFIELD {
        public SPP_FIELD(CSR register, int startBitInclusive, int endBitInclusive) {
            super(register, startBitInclusive, endBitInclusive);
        }
        public PRIVILEGE_MODE getLastPrivilegeMode() {
            return (get() == 1) ? PRIVILEGE_MODE.SUPERVISOR : PRIVILEGE_MODE.USER;
        }
    }

    /**
     * MPP field: 2-bit (0b00 = User, 0b01 = Supervisor, 0b11 = Machine)
     */
    public class MPP extends BITFIELD {
        public MPP(CSR register, int startBitInclusive, int endBitInclusive) {
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
    public void write(long value) {
        super.write(value);
    }
}
