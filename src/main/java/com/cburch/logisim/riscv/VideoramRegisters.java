package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;

public class VideoramRegisters implements InstanceData, Cloneable {

    private Value lastClock;

    long registers[] =
            new long[]
            {0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0,0};

    public VideoramRegisters(Value lastClock) {

        this.lastClock = lastClock;

        // Clears registers
        for (int i = 0; i < 32; i++) {
            registers[i] = 0;
        }
    }

    @Override
    public Object clone() {
        return null;
    }

    public static VideoramRegisters get(InstanceState state) {
        VideoramRegisters ret = (VideoramRegisters) state.getData();
        if (ret == null) {
            // If it doesn't yet exist, then we'll set it up with our default
            // values and put it into the circuit state so it can be retrieved
            // in future propagations.
            ret = new VideoramRegisters(null);
            state.setData(ret);
        }
        return ret;
    }

    public long get(int index) {
        return registers[index];
    }

    public void set(int index, long value) {
        registers[index] = value;
    }

    /** Updates the last clock observed, returning true if triggered. */
    public boolean updateClock(Value value) {
        Value old = lastClock;
        lastClock = value;
        return old == Value.FALSE && value == Value.TRUE;
    }
}
