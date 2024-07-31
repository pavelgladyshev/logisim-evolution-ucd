package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;

public class CounterInterruptRegisters implements InstanceData, Cloneable {

    private Value lastClock;
    private long counter;
    private long counterComparator;

    public CounterInterruptRegisters(Value lastClock) {
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

    public static CounterInterruptRegisters get(InstanceState state) {
        CounterInterruptRegisters ret = (CounterInterruptRegisters) state.getData();
        if (ret == null) {
            ret = new CounterInterruptRegisters(Value.FALSE);
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
