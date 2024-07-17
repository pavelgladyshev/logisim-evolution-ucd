package com.cburch.logisim.riscv;
public class ControlAndStatusRegisters {

    CSR[] registers = new CSR[4096]; // reserve space for 4096 CSRs

    ControlAndStatusRegisters() {
        //csr address mapping conventions
        for(int i = 0; i < 4096; i++) {
            if((i >> 10) < 0x3) {
                registers[i] = new CSR_RW(0);
            }
            else registers[i] = new CSR_R(0);
        }
    }

    public long read(rv32imData hartData, int csr) {
        // check privilege level ( illegal-instruction / virtual-instruction exception if invalid) (18.6.1 priv arch.)
        if(checkRequiredAccessPrivilege(hartData, csr)) return get(csr).read();
        else return 0;
    }

    public void write(rv32imData hartData, int csr, long value) {
        //check privilege level ( illegal-instruction / virtual-instruction exception if invalid) (18.6.1 priv arch.)
        //check read-only ( illegal-instruction exception )
        if(checkRequiredAccessPrivilege(hartData, csr) && (get(csr) instanceof CSR_RW)) get(csr).write(value);
        else TrapHandler.throwIllegalInstructionException(hartData);
    }

    public CSR get(int csr) {
        return registers[csr];
    }

    private static int getRequiredAccessPrivilege(int csr) {
        return (csr >> 8) & 0x3;
    }

    private Boolean checkRequiredAccessPrivilege(rv32imData hartData, int csr) {
        Boolean hasAccessPermission = Boolean.TRUE;
        long priv = ((MSTATUS_CSR)MMCSR.getCSR(hartData, MMCSR.MSTATUS)).MPP.get();
        if(priv < getRequiredAccessPrivilege(csr)) {
            TrapHandler.throwIllegalInstructionException(hartData);
            hasAccessPermission = Boolean.FALSE;
        }
        return hasAccessPermission;
    }
}
