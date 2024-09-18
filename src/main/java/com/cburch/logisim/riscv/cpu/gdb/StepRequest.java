package com.cburch.logisim.riscv.cpu.gdb;

import java.io.IOException;

public class StepRequest extends Request {
    private final long steps;
    private long stepsTaken;
    public StepRequest(long steps) throws IOException {
        super();
        this.steps = steps;
        this.stepsTaken = 0;
    }
    public long getSteps() {
        return steps;
    }
    public void incrementStepsTaken() {
        this.stepsTaken++;
    }

    @Override
    public boolean isComplete(){
        return this.steps == stepsTaken;
    }
}
