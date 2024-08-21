package com.cburch.logisim.riscv.plic;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;

public class PLICRegisters implements InstanceData, Cloneable {

    private Value lastClock;
    private int numSources;
    private final int numContexts = 1; // This can be configurable if needed

    private long[] priorityRegisters;
    private long[] pendingRegisters;
    private long[][] enableRegisters;
    private long[] thresholdRegisters;
    private long[] claimRegisters;

    public PLICRegisters(int numSources, Value lastClock) {
        this.numSources = numSources;
        this.lastClock = lastClock;

        // Initialize the PLIC registers based on numSources
        priorityRegisters = new long[numSources];
        pendingRegisters = new long[(numSources + 31) / 32];
        enableRegisters = new long[numContexts][(numSources + 31) / 32];
        thresholdRegisters = new long[numContexts];
        claimRegisters = new long[numContexts];
    }

    @Override
    public Object clone() {
        try {
            PLICRegisters clone = (PLICRegisters) super.clone();
            clone.priorityRegisters = priorityRegisters.clone();
            clone.pendingRegisters = pendingRegisters.clone();
            clone.enableRegisters = enableRegisters.clone();
            clone.thresholdRegisters = thresholdRegisters.clone();
            clone.claimRegisters = claimRegisters.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(); // Shouldn't happen
        }
    }

    public static PLICRegisters get(InstanceState state, int numSources) {
        PLICRegisters ret = (PLICRegisters) state.getData();
        if (ret == null || ret.numSources != numSources) {
            ret = new PLICRegisters(numSources, null);
            state.setData(ret);
        }
        return ret;
    }

    public int getNumContexts() {
        return numContexts;
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
        return (pendingRegisters[word] & (1 << bit)) != 0;
    }

    public void setPending(int index) {
        int word = index / 32;
        int bit = index % 32;
        pendingRegisters[word] |= (1 << bit);
    }

    public void clearPending(int index) {
        int word = index / 32;
        int bit = index % 32;
        pendingRegisters[word] &= ~(1 << bit);
    }

    public void enableInterrupt(int context, int index) {
        int word = index / 32;
        int bit = index % 32;
        enableRegisters[context][word] |= (1 << bit);
    }

    public void disableInterrupt(int context, int index) {
        int word = index / 32;
        int bit = index % 32;
        enableRegisters[context][word] &= ~(1 << bit);
    }

    public boolean isInterruptEnabled(int context, int index) {
        int word = index / 32;
        int bit = index % 32;
        return (enableRegisters[context][word] & (1 << bit)) != 0;
    }

    public void setThreshold(int context, long threshold) {
        thresholdRegisters[context] = threshold;
    }

    public long getThreshold(int context) {
        return thresholdRegisters[context];
    }

    public long claimInterrupt(int context) {
        for (int i = 0; i < numSources; i++) {
            if (isPending(i) && priorityRegisters[i] > thresholdRegisters[context]) {
                clearPending(i);
                claimRegisters[context] = i;
                return i;
            }
        }
        return 0; // No interrupt to claim
    }

    public void completeInterrupt(int context, long irq) {
        claimRegisters[context] = 0; // Clear claim register after completion
    }

    public boolean updateClock(Value value) {
        Value old = lastClock;
        lastClock = value;
        return old == Value.FALSE && value == Value.TRUE;
    }

    public long[] getClaimRegisters() {
        return claimRegisters;
    }
}
