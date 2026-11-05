package com.cburch.logisim.riscv.timer;

import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.riscv.DataBus;
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
    public static final int DATA_OUT = 3;
    public static final int INTERRUPT_OUT = 4;
    public static final int RESET = 5;
    public static final int CLOCK = 6;
    public static final int DATA_IN = 7;

    public static final long RISCV_MTIMECMP_ADDR_DEFAULT = (0x2000000 + 0x4000);

    public static final Attribute<Long>  RISCV_MTIMECMP_ADDR =
            Attributes.forHexLong("mtimecmpaddr", S.getter("timerMtimecmpAddr"));

    public static final long RISCV_MTIME_ADDR_DEFAULT = (0x2000000 + 0xbff8);

    public static final Attribute<Long>  RISCV_MTIME_ADDR =
            Attributes.forHexLong("mtimeaddr", S.getter("timerMtimeAddr"));

    public static final Attribute<Boolean> ATTR_HEX_REGS =
            Attributes.forBoolean("hexRegisters", S.getter("timerHexRegisters"));


    private static final Value ZERO_32 = Value.createKnown(32, 0);

    // The Timer's box: its title and registers at the top, above its ports, which have their labels beside them
    private static final int TOP = -116;
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 15);
    private static final Font PORT_FONT = new Font("SansSerif", Font.PLAIN, 10);

    public Timer() {
        // on an FPGA (Verilog) the Timer is TimerHdlGeneratorFactory's module
        super(_ID, new TimerHdlGeneratorFactory());
        setOffsetBounds(Bounds.create(-40, TOP, 90, 50 - TOP));
        setPorts(ports());

        setAttributes(
                new Attribute[] {RISCV_MTIMECMP_ADDR, RISCV_MTIME_ADDR, ATTR_HEX_REGS},
                new Object[] {Long.valueOf(RISCV_MTIMECMP_ADDR_DEFAULT), Long.valueOf(RISCV_MTIME_ADDR_DEFAULT), false});
    }

    /** The Timer reads on Data In and writes on Data Out, which is 0 while it is not read (DataBus). */
    private static Port[] ports() {
        Port[] ports = new Port[8];
        ports[ADDRESS] = new Port(-40, -10, Port.INPUT, 32);
        ports[ADDRESS].setToolTip(S.getter("timerAddress"));

        ports[READ_ENABLE] = new Port(-40, 0, Port.INPUT, 1);
        ports[READ_ENABLE].setToolTip(S.getter("timerReadEnable"));

        ports[WRITE_ENABLE] = new Port(-40, 10, Port.INPUT, 1);
        ports[WRITE_ENABLE].setToolTip(S.getter("timerWriteEnable"));

        ports[DATA_OUT] = new Port(50, 0, Port.OUTPUT, 32);
        ports[DATA_OUT].setToolTip(S.getter("timerDataOut"));

        ports[DATA_IN] = new Port(40, 50, Port.INPUT, 32);   // bottom edge, right of Clock
        ports[DATA_IN].setToolTip(S.getter("timerDataIn"));

        ports[INTERRUPT_OUT] = new Port(50, 20, Port.OUTPUT, 1);
        ports[INTERRUPT_OUT].setToolTip(S.getter("timerInterruptRequestOut"));

        ports[RESET] = new Port(-10, 50, Port.INPUT, 1);
        ports[RESET].setToolTip(S.getter("timerReset"));

        ports[CLOCK] = new Port(10, 50, Port.INPUT, 1);
        ports[CLOCK].setToolTip(S.getter("timerClock"));

        return ports;
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        painter.drawBounds();
        painter.drawClock(CLOCK, Direction.NORTH);

        final var loc = painter.getLocation();
        final var x = loc.getX();
        final var y = loc.getY();
        final var graphics = (Graphics2D) painter.getGraphics();
        final var font = graphics.getFont();
        graphics.setFont(PORT_FONT);
        graphics.setColor(Color.BLACK);
        painter.drawPort(ADDRESS, "Address", Direction.EAST);
        painter.drawPort(READ_ENABLE, "Read", Direction.EAST);
        painter.drawPort(WRITE_ENABLE, "Write", Direction.EAST);
        painter.drawPort(DATA_OUT, "Data out", Direction.WEST);
        painter.drawPort(INTERRUPT_OUT, "IRQ", Direction.WEST);
        painter.drawPort(RESET);
        painter.drawPort(DATA_IN);
        // above the bottom edge, clear of the clock's triangle: Data In's at the corner, where it has room
        GraphicsUtil.drawText(graphics, "Reset", x - 10, y + 45, GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
        GraphicsUtil.drawText(graphics, "Data in", x + 47, y + 45, GraphicsUtil.H_RIGHT, GraphicsUtil.V_BASELINE);
        graphics.setFont(font);

        final var posX = x - 30;
        final var posY = y + TOP + 52;   // the registers end 20 above Address
        GraphicsUtil.drawText(graphics, TITLE_FONT, "Timer", posX + 33, posY - 42, 0, 0, Color.black, Color.WHITE);
        if (painter.getShowState()) {
            TimerRegisters regs = TimerRegisters.get(painter);
            final var hex = painter.getAttributeValue(ATTR_HEX_REGS);
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
            long dataValue = state.getPortValue(DATA_IN).toLongValue() & 0xFFFFFFFFL;
            if (address == mtimecmpAddr) {
                regs.setCounterComparator(dataValue);
            } else if (address == mtimeAddr) {
                regs.setCounter(dataValue);
            }
        }

        // Handle register read: Data Out is 0 unless a register is read
        if (state.getPortValue(READ_ENABLE) == Value.TRUE) {
            if (address == mtimecmpAddr) {
                state.setPort(DATA_OUT, Value.createKnown(32, regs.getCounterComparator()), 9);
            } else if (address == mtimeAddr) {
                state.setPort(DATA_OUT, Value.createKnown(32, regs.getCounter()), 9);
            } else {
                state.setPort(DATA_OUT, ZERO_32, 9);
            }
        } else {
            state.setPort(DATA_OUT, ZERO_32, 9);
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
