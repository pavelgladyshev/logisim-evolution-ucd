package com.cburch.sample;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;

class Pc implements InstanceData, Cloneable {

    private static final BitWidth BIT_WIDTH = BitWidth.create(32);

    /** The last clock input value observed. */
    private Value lastClock;
    /** The current value emitted by the counter. */
    private Value value;

    /** Constructs a state with the given values. */
    public Pc(Value lastClock, Value value) {
        this.lastClock = lastClock;
        this.value = value;
    }

    /**
     * Retrieves the state associated with this counter in the circuit state, generating the state if
     * necessary.
     */
    public static Pc get(InstanceState state, BitWidth width) {
        Pc ret = (Pc) state.getData();
        if (ret == null) {
            // If it doesn't yet exist, then we'll set it up with our default
            // values and put it into the circuit state so it can be retrieved
            // in future propagations.
            ret = new Pc(null, Value.createKnown(width, 0));
            state.setData(ret);
        } else if (!ret.value.getBitWidth().equals(width)) {
            ret.value = ret.value.extendWidth(width.getWidth(), Value.FALSE);
        }
        return ret;
    }

    /** Returns a copy of this object. */
    @Override
    public Object clone() {
        // We can just use what super.clone() returns: The only instance
        // variables are
        // Value objects, which are immutable, so we don't care that both the
        // copy
        // and the copied refer to the same Value objects. If we had mutable
        // instance
        // variables, then of course we would need to clone them.
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    /** Returns the current value emitted by the counter. */
    public Value getValue() {
        return value;
    }

    /** Updates the current value emitted by the counter. */
    public void setValue(Value value) {
        this.value = value;
    }

    /** Updates the last clock observed, returning true if triggered. */
    public boolean updateClock(Value value) {
        Value old = lastClock;
        lastClock = value;
        return old == Value.FALSE && value == Value.TRUE;
    }

    public void updatePc()
    {
        /// For now??
        setValue(Value.createKnown(32, value.toLongValue()+4));
    }
    public static BitWidth getBitWidth()
    {
        return BIT_WIDTH;
    }
}
