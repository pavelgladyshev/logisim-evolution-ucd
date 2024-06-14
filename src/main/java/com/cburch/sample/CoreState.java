package com.cburch.sample;

import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;

public class CoreState implements InstanceData, Cloneable {

    public CoreState(Cpu cpu) {
        super();
    }

    public static CoreState get(InstanceState is, Cpu cpu) {
        return null;
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException var2) {
            return null;
        }
    }
}
