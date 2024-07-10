package com.cburch.logisim.riscv;



public class ControlAndStatusRegisters {

    CSR[] registers = new CSR[4096]; // reserve space for 4096 CSRs

    ControlAndStatusRegisters() {
        //set implemented CSSRs

        //set non-implemented CSSRs to (read-only) 0
        for(int i = 0; i < 4096; i++) if (registers[i] == null) registers[i] = new CSR_R(0);

        //csr address mapping conventions
        for(int i = 0; i < 4096; i++) {
            if((i >> 10) < 0x2) {
                registers[i] = new CSR_RW(0);
            }
        }
    }

    public long read(int index) {
        // check privilege level ( illegal-instruction / virtual-instruction exception if invalid) (18.6.1 priv arch.)
        return registers[index].read();
    }

    public void write(int index, long value) {
        //check privilege level ( illegal-instruction / virtual-instruction exception if invalid) (18.6.1 priv arch.)
        //check read-only ( illegal-instruction exception )
        registers[index].write(value);
    }
}
