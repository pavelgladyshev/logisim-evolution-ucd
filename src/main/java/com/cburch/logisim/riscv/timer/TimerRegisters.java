package com.cburch.logisim.riscv.timer;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;

public class TimerRegisters implements InstanceData, Cloneable {

    private Value lastClock;
    private long counter;
    private long counterComparator;

    public TimerRegisters(Value lastClock) {
        this.lastClock = lastClock;
        this.counter = 0;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public static TimerRegisters get(InstanceState state) {
        TimerRegisters ret = (TimerRegisters) state.getData();
        if (ret == null) {
            ret = new TimerRegisters(Value.FALSE);
            state.setData(ret);
        }
        return ret;
    }

    public long getCounter() {
        return counter;
    }

    public void setCounter(long value) {
        counter = value;
    }

    public long getCounterComparator() {
        return counterComparator;
    }

    public void setCounterComparator(long value) {
        counterComparator = value;
    }

    public boolean updateClock(Value value) {
        Value old = lastClock;
        lastClock = value;
        return old == Value.FALSE && value == Value.TRUE;
    }
}
