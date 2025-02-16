package com.cburch.logisim.riscv.timer;

import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.*;

import static com.cburch.logisim.riscv.Strings.S;

public class Timer extends InstanceFactory {

    /**
     * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
     * prevent project files from loading.
     *
     * <p>Identifier value must MUST be unique string among all tools.
     */
    public static final String _ID = "Timer";

    public static final int ADDRESS = 0;
    public static final int STORE = 1;
    public static final int LOAD = 2;
    public static final int DATA_IN = 3;
    public static final int INTERRUPT_OUT = 4;
    public static final int RESET = 5;
    public static final int CLOCK = 6;
    public static final int DATA_OUT = 7;

    public static final long RISCV_MTIMECMP_ADDR_DEFAULT = (0x2000000 + 0x4000);

    public static final Attribute<Long>  RISCV_MTIMECMP_ADDR =
            Attributes.forHexLong("mtimecmpaddr", S.getter("timerMtimecmpAddr"));

    public static final long RISCV_MTIME_ADDR_DEFAULT = (0x2000000 + 0xbff8);

    public static final Attribute<Long>  RISCV_MTIME_ADDR =
            Attributes.forHexLong("mtimeaddr", S.getter("timerMtimeAddr"));

    public static final Attribute<Boolean> ATTR_HEX_REGS =
            Attributes.forBoolean("hexRegisters", S.getter("timerHexRegisters"));


    static final Value HiZ32 = Value.createUnknown(BitWidth.create(32));

    public Timer() {
        super(_ID);
        setOffsetBounds(Bounds.create(-40, -50, 90, 100));

        Port[] ps = new Port[8];
        ps[ADDRESS] = new Port(-40, -10, Port.INPUT, 32);
        ps[STORE] = new Port(-40, 0, Port.INPUT, 1);
        ps[LOAD] = new Port(-40, 10, Port.INPUT, 1);
        ps[DATA_IN] = new Port(-40, 20, Port.INPUT, 32);
        ps[INTERRUPT_OUT] = new Port(50, 20, Port.OUTPUT, 1);
        ps[RESET] = new Port(-10, 50, Port.INPUT, 1);
        ps[CLOCK] = new Port(10, 50, Port.INPUT, 1);
        ps[DATA_OUT] = new Port(50, 0, Port.OUTPUT, 32);

        ps[ADDRESS].setToolTip(S.getter("timerAddressIn"));
        ps[STORE].setToolTip(S.getter("timerStore"));
        ps[LOAD].setToolTip(S.getter("timerLoad"));
        ps[DATA_IN].setToolTip(S.getter("timerDataIn"));
        ps[INTERRUPT_OUT].setToolTip(S.getter("timerInterruptRequestOut"));
        ps[RESET].setToolTip(S.getter("timerReset"));
        ps[CLOCK].setToolTip(S.getter("timerClock"));
        ps[DATA_OUT].setToolTip(S.getter("timerDataOut"));
        setPorts(ps);

        setAttributes(
                new Attribute[] {RISCV_MTIMECMP_ADDR, RISCV_MTIME_ADDR, ATTR_HEX_REGS},
                new Object[] {Long.valueOf(RISCV_MTIMECMP_ADDR_DEFAULT), Long.valueOf(RISCV_MTIME_ADDR_DEFAULT), false});
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        painter.drawBounds();
        painter.drawPorts();

        if (painter.getShowState()) {
            final var bds = painter.getBounds();
            TimerRegisters regs = TimerRegisters.get(painter);
            final var graphics = (Graphics2D) painter.getGraphics();
            final var posX = bds.getX() + 10;
            final var posY = bds.getY() + 50; // Adjusted to fit the text properly within the bounds
            final var hex = painter.getAttributeValue(ATTR_HEX_REGS);
            Font font = new Font("SansSerif", Font.BOLD, 15);

            GraphicsUtil.drawText(graphics, font, "Timer", posX+33, posY-42, 0, 0, Color.black, Color.WHITE);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(posX-3, posY-28, 75, 17);
            GraphicsUtil.drawText(graphics, "mtime", posX+35, posY-22, 0, 0, Color.YELLOW, Color.BLUE);
            graphics.setColor(Color.BLACK);
            graphics.drawRect(posX-3, posY-28, 75, 17);
            GraphicsUtil.drawText(graphics, String.format(hex ? "0x%08x" : "%d" ,(int)(regs.getCounter() & 0xffffffff)), posX+35, posY-4, 0, 0, Color.BLUE, Color.WHITE);
            graphics.setColor(Color.BLACK);
            graphics.drawRect(posX-3, posY-11, 75, 17);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(posX-3, posY+10, 75, 17);
            GraphicsUtil.drawText(graphics, "mtimecmp", posX+35, posY+16, 0, 0, Color.YELLOW, Color.BLUE);
            graphics.setColor(Color.BLACK);
            graphics.drawRect(posX-3, posY+10, 75, 17);
            GraphicsUtil.drawText(graphics, String.format(hex ? "0x%08x" : "%d" ,(int)(regs.getCounterComparator() & 0xffffffff)), posX+35, posY+34, 0, 0, Color.BLUE, Color.WHITE);
            graphics.setColor(Color.BLACK);
            graphics.drawRect(posX-3, posY+27, 75, 17);
        }
    }


    @Override
    public void propagate(InstanceState state) {

        final var cur = TimerRegisters.get(state);

        // Check if clock signal is changing from low/false to high/true
        final var trigger = cur.updateClock(state.getPortValue(6));

        if(state.getPortValue(RESET) == Value.TRUE) {
            cur.setCounter(0);
            cur.setCounterComparator(0xffffffffL);
        }

        if (state.getPortValue(STORE) == Value.TRUE && trigger &&
                (state.getPortValue(ADDRESS).toLongValue() == state.getAttributeValue(RISCV_MTIMECMP_ADDR) ))  {
            cur.setCounterComparator((state.getPortValue(DATA_IN).toLongValue()) & 0xFFFFFFFFL);
        } else if (state.getPortValue(STORE) == Value.TRUE && trigger &&
                (state.getPortValue(ADDRESS).toLongValue() == state.getAttributeValue(RISCV_MTIME_ADDR) ))  {
            cur.setCounter((state.getPortValue(DATA_IN).toLongValue()) & 0xFFFFFFFFL);
        }

        if (state.getPortValue(LOAD) == Value.TRUE &&
                (state.getPortValue(ADDRESS).toLongValue() == state.getAttributeValue(RISCV_MTIMECMP_ADDR) ))  {
            state.setPort(DATA_OUT, Value.createKnown(32, cur.getCounterComparator()), 9);
        } else if (state.getPortValue(LOAD) == Value.TRUE &&
                (state.getPortValue(ADDRESS).toLongValue() == state.getAttributeValue(RISCV_MTIME_ADDR) ))  {
            state.setPort(DATA_OUT, Value.createKnown(32, cur.getCounter()), 9);
        } else {
            state.setPort(DATA_OUT, HiZ32, 9);
        }

        if (trigger) {
            cur.setCounter((cur.getCounter() + 1) & 0x7FFFFFFFL);

            if (cur.getCounter() >= cur.getCounterComparator()) {
                state.setPort(INTERRUPT_OUT, Value.TRUE, 9);
            } else {
                state.setPort(INTERRUPT_OUT, Value.FALSE, 9);
            }
        }
    }
}
