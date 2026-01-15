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

    // Port indices
    public static final int ADDRESS = 0;
    public static final int READ_ENABLE = 1;
    public static final int WRITE_ENABLE = 2;
    public static final int DATA = 3;
    public static final int INTERRUPT_OUT = 4;
    public static final int RESET = 5;
    public static final int CLOCK = 6;

    public static final long RISCV_MTIMECMP_ADDR_DEFAULT = (0x2000000 + 0x4000);

    public static final Attribute<Long>  RISCV_MTIMECMP_ADDR =
            Attributes.forHexLong("mtimecmpaddr", S.getter("timerMtimecmpAddr"));

    public static final long RISCV_MTIME_ADDR_DEFAULT = (0x2000000 + 0xbff8);

    public static final Attribute<Long>  RISCV_MTIME_ADDR =
            Attributes.forHexLong("mtimeaddr", S.getter("timerMtimeAddr"));

    public static final Attribute<Boolean> ATTR_HEX_REGS =
            Attributes.forBoolean("hexRegisters", S.getter("timerHexRegisters"));


    private static final Value HI_Z_32 = Value.createUnknown(BitWidth.create(32));

    public Timer() {
        super(_ID);
        setOffsetBounds(Bounds.create(-40, -50, 90, 100));

        Port[] ports = new Port[7];
        ports[ADDRESS] = new Port(-40, -10, Port.INPUT, 32);
        ports[ADDRESS].setToolTip(S.getter("timerAddress"));

        ports[READ_ENABLE] = new Port(-40, 0, Port.INPUT, 1);
        ports[READ_ENABLE].setToolTip(S.getter("timerReadEnable"));

        ports[WRITE_ENABLE] = new Port(-40, 10, Port.INPUT, 1);
        ports[WRITE_ENABLE].setToolTip(S.getter("timerWriteEnable"));

        ports[DATA] = new Port(50, 0, Port.INOUT, 32);
        ports[DATA].setToolTip(S.getter("timerData"));

        ports[INTERRUPT_OUT] = new Port(50, 20, Port.OUTPUT, 1);
        ports[INTERRUPT_OUT].setToolTip(S.getter("timerInterruptRequestOut"));

        ports[RESET] = new Port(-10, 50, Port.INPUT, 1);
        ports[RESET].setToolTip(S.getter("timerReset"));

        ports[CLOCK] = new Port(10, 50, Port.INPUT, 1);
        ports[CLOCK].setToolTip(S.getter("timerClock"));

        setPorts(ports);

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
        final var regs = TimerRegisters.get(state);

        // Check if clock signal is changing from low/false to high/true
        final var clockTriggered = regs.updateClock(state.getPortValue(CLOCK));

        if (state.getPortValue(RESET) == Value.TRUE) {
            regs.setCounter(0);
            regs.setCounterComparator(0xFFFFFFFFL);
        }

        long address = state.getPortValue(ADDRESS).toLongValue();
        long mtimecmpAddr = state.getAttributeValue(RISCV_MTIMECMP_ADDR);
        long mtimeAddr = state.getAttributeValue(RISCV_MTIME_ADDR);

        // Handle register write on clock edge
        if (state.getPortValue(WRITE_ENABLE) == Value.TRUE && clockTriggered) {
            long dataValue = state.getPortValue(DATA).toLongValue() & 0xFFFFFFFFL;
            if (address == mtimecmpAddr) {
                regs.setCounterComparator(dataValue);
            } else if (address == mtimeAddr) {
                regs.setCounter(dataValue);
            }
        }

        // Handle register read (directly drives data bus when enabled)
        if (state.getPortValue(READ_ENABLE) == Value.TRUE) {
            if (address == mtimecmpAddr) {
                state.setPort(DATA, Value.createKnown(32, regs.getCounterComparator()), 9);
            } else if (address == mtimeAddr) {
                state.setPort(DATA, Value.createKnown(32, regs.getCounter()), 9);
            } else {
                state.setPort(DATA, HI_Z_32, 9);
            }
        } else {
            state.setPort(DATA, HI_Z_32, 9);
        }

        // Increment counter on clock edge
        if (clockTriggered) {
            regs.setCounter((regs.getCounter() + 1) & 0x7FFFFFFFL);

            if (regs.getCounter() >= regs.getCounterComparator()) {
                state.setPort(INTERRUPT_OUT, Value.TRUE, 9);
            } else {
                state.setPort(INTERRUPT_OUT, Value.FALSE, 9);
            }
        }
    }
}
