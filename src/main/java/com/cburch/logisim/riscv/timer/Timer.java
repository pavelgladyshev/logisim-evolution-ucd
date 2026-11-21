package com.cburch.logisim.riscv.timer;

import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.riscv.DataBus;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.*;

import static com.cburch.logisim.riscv.Strings.S;

/**
 * The RISC-V machine timer on a data bus of 32 bits: mtime, which counts the rising edges of the clock, and
 * mtimecmp, of 64 bits each, and an interrupt request while mtime >= mtimecmp. Each register is two words: its low
 * word at the address of its attribute and its high word 4 above (with the default addresses, where QEMU's virt
 * machine has them). TimerRegisters has the rules; on an FPGA the Timer is riscvTimer.v, which follows them too.
 */
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
    // A register: its name on a bar, and below it its 64 bits on up to two lines of ten characters
    private static final Font NAME_FONT = new Font("SansSerif", Font.BOLD, 10);
    private static final Font VALUE_FONT = new Font("Monospaced", Font.PLAIN, 11);
    private static final int REG_LEFT = -37;
    private static final int REG_WIDTH = 84;
    private static final int NAME_HEIGHT = 12;
    private static final int VALUE_HEIGHT = 25;
    private static final int LINE_HEIGHT = 11;
    private static final int LINE_CHARS = 10;

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
        ports[RESET].setToolTip(S.getter("timerReset", String.format("0x%016x", TimerRegisters.MTIMECMP_RESET)));

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

        GraphicsUtil.drawText(graphics, TITLE_FONT, "Timer", x + 5, y + TOP + 10, 0, 0, Color.black, Color.WHITE);
        if (painter.getShowState()) {
            TimerRegisters regs = TimerRegisters.get(painter);
            final var hex = painter.getAttributeValue(ATTR_HEX_REGS);
            // one below the other, under the title; they end 10 above Address, clear of its label
            paintRegister(graphics, x, y + TOP + 20, "mtime", regs.getMtime(), hex);
            paintRegister(graphics, x, y + TOP + 59, "mtimecmp", regs.getMtimecmp(), hex);
            graphics.setFont(font);
            graphics.setColor(Color.BLACK);
        }
    }

    /** A register's name on a bar whose top is at y, and its value in a frame below the bar. */
    private static void paintRegister(Graphics2D graphics, int x, int y, String name, long value, boolean hex) {
        final var left = x + REG_LEFT;
        final var middle = left + REG_WIDTH / 2;
        graphics.setColor(Color.BLUE);
        graphics.fillRect(left, y, REG_WIDTH, NAME_HEIGHT);
        graphics.setColor(Color.YELLOW);
        GraphicsUtil.drawText(graphics, NAME_FONT, name, middle, y + NAME_HEIGHT / 2 - 1,
                GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER);
        graphics.setColor(Color.BLACK);
        graphics.drawRect(left, y, REG_WIDTH, NAME_HEIGHT);
        graphics.drawRect(left, y + NAME_HEIGHT, REG_WIDTH, VALUE_HEIGHT);
        graphics.setColor(Color.BLUE);
        graphics.setFont(VALUE_FONT);
        final var lines = registerLines(value, hex);
        // the lines end at the same column, a little right of the middle for the two characters of 0x
        final var right = middle + graphics.getFontMetrics().stringWidth("0") * LINE_CHARS / 2;
        final var first = y + NAME_HEIGHT + (VALUE_HEIGHT + 1 - LINE_HEIGHT * lines.length) / 2 + LINE_HEIGHT / 2;
        for (var i = 0; i < lines.length; i++) {
            GraphicsUtil.drawText(graphics, lines[i], right, first + LINE_HEIGHT * i - 1,
                    GraphicsUtil.H_RIGHT, GraphicsUtil.V_CENTER);
        }
    }

    /**
     * The 64 bits of a register as the Timer shows them, in lines of ten characters at most. In hexadecimal, its
     * high word after 0x and its low word below it. In decimal, unsigned: one line up to ten digits (any value
     * below 2^32), and the digits before the last ten on a line above them otherwise.
     */
    static String[] registerLines(long value, boolean hex) {
        if (hex) {
            return new String[] {String.format("0x%08x", value >>> 32), String.format("%08x", value & 0xffffffffL)};
        }
        final var digits = Long.toUnsignedString(value);
        final var split = digits.length() - LINE_CHARS;
        return split <= 0 ? new String[] {digits} : new String[] {digits.substring(0, split), digits.substring(split)};
    }


    @Override
    public void propagate(InstanceState state) {
        final var regs = TimerRegisters.get(state);

        // Check if clock signal is changing from low/false to high/true
        final var clockTriggered = regs.updateClock(state.getPortValue(CLOCK));

        final var reset = state.getPortValue(RESET) == Value.TRUE;
        final var word = TimerRegisters.select(state.getPortValue(ADDRESS),
                state.getAttributeValue(RISCV_MTIMECMP_ADDR), state.getAttributeValue(RISCV_MTIME_ADDR));

        if (clockTriggered) {
            // A rising edge: Reset alone, or else a write to the addressed word and then mtime counts on
            final var write = state.getPortValue(WRITE_ENABLE) == Value.TRUE;
            regs.clockEdge(reset, write ? word : TimerRegisters.Word.NONE, state.getPortValue(DATA_IN).toLongValue());
        } else if (reset) {
            // Reset holds the registers between edges too
            regs.reset();
        }

        // Both outputs follow the registers as they are now. Data Out is 0 unless a register is read
        final var read = state.getPortValue(READ_ENABLE) == Value.TRUE;
        state.setPort(DATA_OUT, read ? Value.createKnown(32, regs.read(word)) : ZERO_32, 9);
        state.setPort(INTERRUPT_OUT, regs.isInterruptRequested() ? Value.TRUE : Value.FALSE, 9);
    }
}
