package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.csrs.*;

public class ControlAndStatusRegisters {

    CSR[] registers = new CSR[4096]; // reserve space for 4096 CSRs

    ControlAndStatusRegisters() {

        // Machine-mode CSRs
        registers[MMCSR.MCAUSE.getAddress()] = new MCAUSE_CSR(0);
        registers[MMCSR.MEPC.getAddress()] = new MEPC_CSR(0);
        MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
        registers[MMCSR.MSTATUS.getAddress()] = mstatus;
        registers[MMCSR.MTVEC.getAddress()] = new MTVEC_CSR(0);
        MIE_CSR mie = new MIE_CSR(0);
        registers[MMCSR.MIE.getAddress()] = mie;
        MIP_CSR mip = new MIP_CSR(0);
        registers[MMCSR.MIP.getAddress()] = mip;

        // Machine delegation CSRs
        registers[MMCSR.MEDELEG.getAddress()] = new MEDELEG_CSR(0);
        registers[MMCSR.MIDELEG.getAddress()] = new MIDELEG_CSR(0);

        // Supervisor-mode CSRs
        registers[SCSR.SSTATUS.getAddress()] = new SSTATUS_CSR(mstatus);  // view of MSTATUS
        registers[SCSR.SIE.getAddress()] = new SIE_CSR(mie);              // view of MIE
        registers[SCSR.SIP.getAddress()] = new SIP_CSR(mip);              // view of MIP
        registers[SCSR.STVEC.getAddress()] = new STVEC_CSR(0);
        registers[SCSR.SSCRATCH.getAddress()] = new CSR_RW(0);
        registers[SCSR.SEPC.getAddress()] = new SEPC_CSR(0);
        registers[SCSR.SCAUSE.getAddress()] = new SCAUSE_CSR(0);
        registers[SCSR.STVAL.getAddress()] = new CSR_RW(0);
        registers[SCSR.SATP.getAddress()] = new SATP_CSR(0);

        // CSR address mapping conventions: fill remaining with default RW/R
        for(int i = 0; i < 4096; i++) {
            if(registers[i] == null) {
                if ((i >> 10) < 0x3) {
                    registers[i] = new CSR_RW(0);
                } else registers[i] = new CSR_R(0);
            }
        }
    }

    /**
     * Check if a CSR address encodes a read-only register.
     * RISC-V CSR address bits [11:10] == 0x3 means read-only.
     */
    public static boolean isReadOnly(int csr) {
        return ((csr >> 10) & 0x3) == 0x3;
    }

    /**
     * Upfront access validation for CSR instructions.
     * Checks privilege level and write permission BEFORE any read/write side effects.
     * Returns true if access is permitted, false if an exception was raised.
     *
     * @param willWrite true if the instruction will modify the CSR
     *                  (false for CSRRS/CSRRC with rs1=x0 or CSRRSI/CSRRCI with uimm=0)
     */
    public boolean checkAccess(rv32imData hartData, int csr, boolean willWrite) {
        // Check privilege level using actual current privilege mode
        long priv = hartData.getCurrentPrivilegeMode().getValue();
        if (priv < getRequiredAccessPrivilege(csr)) {
            TrapHandler.throwIllegalInstructionException(hartData);
            return false;
        }
        // Check write permission: read-only CSRs (bits [11:10] == 0x3) cannot be written
        if (willWrite && isReadOnly(csr)) {
            TrapHandler.throwIllegalInstructionException(hartData);
            return false;
        }
        return true;
    }

    /**
     * Read a CSR value. Access control must be validated by checkAccess() before calling this.
     */
    public long read(rv32imData hartData, int csr) {
        return get(csr).read();
    }

    /**
     * Write a CSR value. Access control must be validated by checkAccess() before calling this.
     */
    public void write(rv32imData hartData, int csr, long value) {
        if (get(csr) instanceof CSR_RW) get(csr).write(value);
    }

    public CSR get(int csr) {
        return registers[csr];
    }

    private static int getRequiredAccessPrivilege(int csr) {
        return (csr >> 8) & 0x3;
    }
}
