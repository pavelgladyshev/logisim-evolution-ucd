package com.cburch.logisim.riscv.cpu.gdb;

import java.io.IOException;
import java.util.List;

public class ContinueRequest extends Request {
    private List<Breakpoint> breakpoints;
    public ContinueRequest(List<Breakpoint> breakpoints) throws IOException {
        super();
        this.breakpoints = breakpoints;
    }
    public List<Breakpoint> getBreakpoints() {
        return breakpoints;
    }
    @Override
    public boolean isComplete(){
        return false;
    }
}
