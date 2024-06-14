package com.cburch.sample;

import com.cburch.logisim.instance.InstanceState;

/// TODO
public class RST {
    private static boolean on = true;

    public static boolean getRSTbit(InstanceState state) {
//        on = state.getPortValue(1).equals(Value.create(1, 0));
//        return ;
        return on;
    }

    public static void swapRSTbit() {
        on = !on;
    }
}
