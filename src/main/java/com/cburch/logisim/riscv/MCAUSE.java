package com.cburch.logisim.riscv;

public class MCAUSE extends CSR_RW {

    protected BITFIELD EXCEPTION_CODE;
    protected BITFIELD INTERRUPT;

    MCAUSE(long initValue) {
        super(initValue);
        EXCEPTION_CODE = new BITFIELD(this, 0,30);
        INTERRUPT = new BITFIELD(this, 31,31);
    }

    public enum TRAP_CAUSE {
        SUPERVISOR_SOFTWARE_INTERRUPT(1,1),
        MACHINE_SOFTWARE_INTERRUPT(1,3),
        SUPERVISOR_TIMER_INTERRUPT(1,5),
        MACHINE_TIMER_INTERRUPT(1,7),
        SUPERVISOR_EXTERNAL_INTERRUPT(1,9),
        MACHINE_EXTERNAL_INTERRUPT(1,11),
        INSTRUCTION_ADDRESS_MISALIGNED(0,0),
        INSTRUCTION_ACCESS_FAULT(0,1),
        ILLEGAL_INSTRUCTION(0,2),
        BREAKPOINT(0,3),
        LOAD_ADDRESS_MISALIGNED(0,4),
        LOAD_ACCESS_FAULT(0,5),
        STORE_ADDRESS_MISALIGNED(0,6),
        STORE_ACCESS_FAULT(0,7),
        ENVIRONMENT_CALL_FROM_U_MODE(0,8),
        ENVIRONMENT_CALL_FROM_S_MODE(0,9),
        ENVIRONMENT_CALL_FROM_M_MODE(0,11),
        INSTRUCTION_PAGE_FAULT(0,12),
        LOAD_PAGE_FAULT(0,13),
        STORE_PAGE_FAULT(0,15);

        private final int interrupt;
        private final int exceptionCode;

        TRAP_CAUSE(int interrupt, int exceptionCode) {
            this.interrupt = interrupt;
            this.exceptionCode = exceptionCode;
        }

        public int getInterrupt() {
            return interrupt;
        }
        public int getExceptionCode() {
            return exceptionCode;
        }
        public Boolean isInterrupt() {
            return interrupt == 1;
        }
    }
}
