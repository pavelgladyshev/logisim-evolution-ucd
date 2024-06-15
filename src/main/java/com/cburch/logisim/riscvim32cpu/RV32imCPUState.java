package com.cburch.logisim.riscvim32cpu;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;

public class RV32imCPUState implements InstanceData, Cloneable
{
    //PC START ON RST
    private static long startingAddress = 0x00400000;

    //PC, GPR, CLK
    private Value lastClock;
    private Value programCounter;

    //INPUT PORTS
    private Value rst;
    private Value dataIn;
    //OUTPUT PORTS
    private Value address;
    private Value dataOut;
    private Value memRead;
    private Value memWrite;

    RV32imCPUState(Value lastClock, Value programCounter){
        this.lastClock = lastClock;
        this.programCounter = programCounter;
    }

    /**
     * Retrieves the state associated with this counter in the circuit state, generating the state if
     * necessary.
     */
    public static RV32imCPUState get(InstanceState state) {
        RV32imCPUState ret = (RV32imCPUState) state.getData();
        BitWidth width = BitWidth.create(32); //PC
        if (ret == null) {
            ret = new RV32imCPUState(null, Value.createKnown(width, startingAddress));
            state.setData(ret);
        }
        else if (!ret.programCounter.getBitWidth().equals(width)) {
            ret.programCounter = ret.programCounter.extendWidth(width.getWidth(), Value.FALSE);
        }
        return ret;
    }

    /*See Gray Counter Example (CounterData.java)*/
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    /** Updates the last clock observed, returning true if triggered. */
    public boolean updateClock(Value value) {
        Value old = lastClock;
        lastClock = value;
        return old == Value.FALSE && value == Value.TRUE;
    }
}
