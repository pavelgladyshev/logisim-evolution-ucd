package com.cburch.logisim.riscv.plic;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;

public class PLICRegisters implements InstanceData, Cloneable {

    private Value lastClock;
    private int numSources;

    private long[] priorityRegisters;
    private long[] pendingRegisters;
    private long[] enableRegisters;
    private long thresholdRegister;
    private long claimRegister;

    public PLICRegisters(int numSources, Value lastClock, long thresholdRegister, long claimRegister) {
        this.numSources = numSources;
        this.lastClock = lastClock;
        this.thresholdRegister = thresholdRegister;
        this.claimRegister = claimRegister;

        priorityRegisters = new long[52];
        pendingRegisters = new long[2];
        enableRegisters = new long[2];
    }

    @Override
    public Object clone() {
        try {
            PLICRegisters clone = (PLICRegisters) super.clone();
            clone.priorityRegisters = priorityRegisters.clone();
            clone.pendingRegisters = pendingRegisters.clone();
            clone.enableRegisters = enableRegisters.clone();
            clone.thresholdRegister = thresholdRegister;
            clone.claimRegister = claimRegister;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public static PLICRegisters get(InstanceState state, int numSources) {
        PLICRegisters ret = (PLICRegisters) state.getData();
        if (ret == null || ret.numSources != numSources) {
            ret = new PLICRegisters(numSources, null, 0, 0);
            state.setData(ret);
        }
        return ret;
    }

    public long getPriority(int index) {
        return priorityRegisters[index];
    }

    public void setPriority(int index, long value) {
        priorityRegisters[index] = value;
    }

    public boolean isPending(int index) {
        int word = index / 32;
        int bit = index % 32;
        return (pendingRegisters[word] & (1L << bit)) != 0;
    }

    public void setPending(int index) {
        int word = index / 32;
        int bit = index % 32;
        pendingRegisters[word] |= (1L << bit);
    }

    public void clearPending(int index) {
        int word = index / 32;
        int bit = index % 32;
        pendingRegisters[word] &= ~(1L << bit);
    }

    public void enableInterrupt(int index) {
        int word = index / 32;
        int bit = index % 32;
        enableRegisters[word] |= (1L << bit);
    }

    public void disableInterrupt(int index) {
        int word = index / 32;
        int bit = index % 32;
        enableRegisters[word] &= ~(1L << bit);
    }

    public boolean isInterruptEnabled(int index) {
        int word = index / 32;
        int bit = index % 32;
        return (enableRegisters[word] & (1L << bit)) != 0;
    }

    public void setThresholdRegister(long threshold) {
        thresholdRegister = threshold;
    }

    public long getThresholdRegister() {
        return thresholdRegister;
    }

    public boolean updateClock(Value value) {
        Value old = lastClock;
        lastClock = value;
        return old == Value.FALSE && value == Value.TRUE;
    }

    public long getClaimRegister() {
        return claimRegister;
    }

    public void setClaimRegister(long claim) {
        claimRegister = claim;
    }

    public long[] getEnableRegisters() {
        return enableRegisters;
    }

    public long[] getPendingRegisters() {
        return pendingRegisters;
    }

}