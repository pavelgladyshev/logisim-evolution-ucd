package com.cburch.logisim.riscv.cpu.gdb;

public interface DebuggerRequest {
    boolean process(long dataIn);   // returns true when processing has finished, or false if more calls are necessary.
}
