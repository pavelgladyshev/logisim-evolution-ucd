package com.cburch.logisim.riscv.videoram;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.*;

import static com.cburch.logisim.riscv.Strings.S;

public class MonochromeVideoram extends InstanceFactory {

    /**
     * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
     * prevent project files from loading.
     *
     * <p>Identifier value must MUST be unique string among all tools.
     */
    public static final String _ID = "Monochrome VideoRAM";

    public static final int STORE = 0;
    public static final int ADDRESS = 1;
    public static final int DATA_IN = 2;
    public static final int LOAD = 3;
    public static final int DATA_OUT = 4;
    public static final int RESET = 5;
    public static final int CLOCK = 6;

    static final Value HiZ32 = Value.createUnknown(BitWidth.create(32));

    public MonochromeVideoram() {
        super(_ID);
        setOffsetBounds(Bounds.create(-30, -10, 30, 330));

        Port[] ps = new Port[39];
        ps[STORE] = new Port(-30, 140, Port.INPUT, 1);
        ps[ADDRESS] = new Port(-30, 150, Port.INPUT, 5);
        ps[DATA_IN] = new Port(-30, 160, Port.INPUT, 32);
        ps[LOAD] = new Port(-30, 170, Port.INPUT, 1);
        ps[DATA_OUT] = new Port(-20, -10, Port.OUTPUT, 32);
        ps[RESET] = new Port(-20, 320, Port.INPUT, 1);
        ps[CLOCK] = new Port(-10, 320, Port.INPUT, 1);

        ps[STORE].setToolTip(S.getter("monoRamStore"));
        ps[ADDRESS].setToolTip(S.getter("monoRamAddress"));
        ps[DATA_IN].setToolTip(S.getter("monoRamDataIn"));
        ps[LOAD].setToolTip(S.getter("monoRamLoad"));
        ps[DATA_OUT].setToolTip(S.getter("monoRamDataOut"));
        ps[RESET].setToolTip(S.getter("monoRamReset"));
        ps[CLOCK].setToolTip(S.getter("monoRamClock"));

        for (int i = 0; i < 32; i++) {
            ps[i+7] = new Port(0, i*10, Port.OUTPUT, 32);
            ps[i+7].setToolTip(S.getter("L_" + i));
        }
        setPorts(ps);
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        painter.drawBounds();
        painter.drawPorts();
    }

    @Override
    public void propagate(InstanceState state) {

        final var cur = VideoramRegisters.get(state);

        if (state.getPortValue(RESET) == Value.TRUE) {
            for (int i = 0; i < 32; i++) {
                cur.set(i, 0);
                state.setPort(i + 7, Value.createKnown(32, cur.get(i)), 9);
            }
        }

        final var trigger = cur.updateClock(state.getPortValue(CLOCK));

        if (state.getPortValue(LOAD) == Value.TRUE) {
            state.setPort(DATA_OUT, Value.createKnown(32, cur.get((int) state.getPortValue(ADDRESS).toLongValue())), 9);
        } else {
            state.setPort(DATA_OUT, HiZ32, 9);
        }

        if (state.getPortValue(STORE) == Value.TRUE && trigger) {
            int index = (int) state.getPortValue(ADDRESS).toLongValue();
            cur.set(index, state.getPortValue(DATA_IN).toLongValue() & 0xFFFFFFFFFL);
            for (int i = 0; i < 32; i++) {
                state.setPort(i + 7, Value.createKnown(32, cur.get(i)), 9);
            }
        }

    }
}
