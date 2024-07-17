package com.cburch.logisim.riscv;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.*;

import static com.cburch.logisim.std.Strings.S;

public class CounterInterrupt extends InstanceFactory {

    /**
     * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
     * prevent project files from loading.
     *
     * <p>Identifier value must MUST be unique string among all tools.
     */
    public static final String _ID = "counter_interrupt";

    public static final int ADDRESS = 0;
    public static final int STORE = 1;
    public static final int DATA_IN = 2;
    public static final int INTERRUPT_OUT = 3;
    public static final int RESET = 4;
    public static final int CLOCK = 5;
    public static final int DATA_OUT = 6;

    static final Value HiZ32 = Value.createUnknown(BitWidth.create(32));

    public CounterInterrupt() {
        super(_ID);
        setOffsetBounds(Bounds.create(-30, -10, 50, 60));

        Port ps[] = new Port[7];
        ps[ADDRESS] = new Port(-30, 10, Port.INPUT, 32);
        ps[STORE] = new Port(-30, 20, Port.INPUT, 1);
        ps[DATA_IN] = new Port(-30, 30, Port.INPUT, 32);
        ps[INTERRUPT_OUT] = new Port(20, 20, Port.INPUT, 1);
        ps[RESET] = new Port(-10, 50, Port.INPUT, 1);
        ps[CLOCK] = new Port(0, 50, Port.INPUT, 1);
        ps[DATA_OUT] = new Port(0, -10, Port.INPUT, 32);

        ps[ADDRESS].setToolTip(S.getter("counterInterruptAddress"));
        ps[STORE].setToolTip(S.getter("counterInterruptStore"));
        ps[DATA_IN].setToolTip(S.getter("counterInterruptDataIn"));
        ps[INTERRUPT_OUT].setToolTip(S.getter("counterInterruptOut"));
        ps[RESET].setToolTip(S.getter("counterInterruptReset"));
        ps[CLOCK].setToolTip(S.getter("counterInterruptClock"));
        ps[DATA_OUT].setToolTip(S.getter("counterDataOut"));
        setPorts(ps);
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        painter.drawBounds();
        painter.drawPorts();

        if (painter.getShowState()) {
            final var bds = painter.getBounds();
            final var graphics = (Graphics2D) painter.getGraphics();
            final var posX = bds.getX() + 10;
            final var posY = bds.getY() + 50; // Adjusted to fit the text properly within the bounds

            Font font = new Font("Serif", Font.BOLD, 11); // Changed font size to 12

            GraphicsUtil.drawText(graphics, font, "Counter", posX+15, posY-25, 0, 0, Color.black, Color.WHITE);
            GraphicsUtil.drawText(graphics, font, "Interrupt", posX+15, posY-15, 0, 0, Color.black, Color.WHITE);
        }
    }


    @Override
    public void propagate(InstanceState state) {

        final var cur = CounterInterruptRegisters.get(state);

        // Check if clock signal is changing from low/false to high/true
        final var trigger = cur.updateClock(state.getPortValue(5));

        if(state.getPortValue(RESET) == Value.TRUE) {
            cur.setCounter(0);
            cur.setCounterComparator(0xFFFFFFFFL);
        }

        if (state.getPortValue(STORE) == Value.TRUE &&
                (state.getPortValue(ADDRESS).toLongValue() == 0xFFFF0C00L) ) {
            state.setPort(DATA_OUT, Value.createKnown(32, cur.getCounter()), 9);
            cur.setCounterComparator(state.getPortValue(DATA_IN).toLongValue());
        } else {
            state.setPort(DATA_OUT, HiZ32, 9);
        }

        if (trigger) {

            cur.setCounter(cur.getCounter() + 1);

            if (cur.getCounter() >= cur.getCounterComparator()) {
                state.setPort(INTERRUPT_OUT, Value.TRUE, 9);
                cur.setCounterComparator(0xFFFFFFFFL);
            } else {
                state.setPort(INTERRUPT_OUT, Value.FALSE, 9);
            }
        }
    }
}
