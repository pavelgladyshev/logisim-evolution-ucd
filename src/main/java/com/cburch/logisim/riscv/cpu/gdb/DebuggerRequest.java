package com.cburch.logisim.riscv.cpu.gdb;

public interface DebuggerRequest {
    boolean process();   // returns true when processing has finished, or false if more calls are necessary.
}
