package com.cburch.logisim.riscv.plic;

import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.*;

import static com.cburch.logisim.std.Strings.S;

public class PLIC extends InstanceFactory {

    public static final String _ID = "plic";

    // Define port indices for control signals
    public static final int STORE = 0;
    public static final int ADDRESS = 1;
    public static final int DATA_IN = 2;
    public static final int DATA_OUT = 3;
    public static final int LOAD = 4;
    public static final int INTERRUPT_OUT = 5;
    public static final int RESET = 6;
    public static final int CLOCK = 7;

    public static final long RISCV_PLIC_ADDR_DEFAULT = 0x0c000000;

    public static final Attribute<Long>  RISCV_PLIC_ADDR =
            Attributes.forHexLong("PLICaddr", S.getter("PLICaddr"));

    // Configurable number of interrupt sources
    private static final Attribute<Integer> ATTR_NUM_SOURCES =
            Attributes.forIntegerRange("numSources", S.getter("PLICNumSources"), 1, 52);

    static final Value HiZ32 = Value.createUnknown(BitWidth.create(32));
    private int shift = 0;

    public PLIC() {
        super(_ID);
        setAttributes(
                        new Attribute[]{RISCV_PLIC_ADDR, ATTR_NUM_SOURCES, StdAttr.LABEL, StdAttr.LABEL_FONT},
                        new Object[]{Long.valueOf(RISCV_PLIC_ADDR_DEFAULT), 2, "", StdAttr.DEFAULT_LABEL_FONT});

        setOffsetBounds(Bounds.create(-30, -10, 30, 230));
    }

    @Override
    protected void configureNewInstance(Instance instance) {
        instance.addAttributeListener();
        computePorts(instance);
    }

    void computePorts(Instance instance) {
        final var attrs = instance.getAttributeSet();
        int numSources = attrs.getValue(ATTR_NUM_SOURCES);
        final var ps = new Port[numSources + 8];

        // Control ports
        ps[STORE] = new Port(-30, 50, Port.INPUT, 1);
        ps[ADDRESS] = new Port(-30, 20, Port.INPUT, 32);
        ps[DATA_IN] = new Port(-30, 110, Port.INPUT, 32);
        ps[DATA_OUT] = new Port(-30, 140, Port.INPUT, 32);
        ps[LOAD] = new Port(-30, 80, Port.INPUT, 1);
        ps[INTERRUPT_OUT] = new Port(10 * (( (numSources == 1 ? 0 : numSources) - 2) / 2), -10, Port.OUTPUT, 1);
        ps[RESET] = new Port(-30, 170, Port.INPUT, 1);
        ps[CLOCK] = new Port(-30, 190, Port.INPUT, 1);

        // Interrupt source ports
        for (int i = 0; i < numSources; i++) {
            final var offs = -10 + i * 10;
            ps[i + 8] = new Port(offs, 220, Port.INPUT, 1);
        }

        ps[STORE].setToolTip(S.getter("PLICStore"));
        ps[ADDRESS].setToolTip(S.getter("PLICAddress"));
        ps[DATA_IN].setToolTip(S.getter("PLICDataIn"));
        ps[LOAD].setToolTip(S.getter("PLICLoad"));
        ps[DATA_OUT].setToolTip(S.getter("PLICDataOut"));
        ps[INTERRUPT_OUT].setToolTip(S.getter("PLICInterruptOut"));
        ps[RESET].setToolTip(S.getter("PLICReset"));
        ps[CLOCK].setToolTip(S.getter("PLICClock"));

        for (int i = 0; i < numSources; i++) {
            ps[i + 8].setToolTip(S.fixedString("Source " + (i+1)));
        }

        shift = 5 * (numSources - 2);
        instance.setPorts(ps);
    }

    @Override
    public Bounds getOffsetBounds(AttributeSet attrsBase) {
        int numSources = attrsBase.getValue(ATTR_NUM_SOURCES);
        return Bounds.create(-30, -10, 30 + 10 * numSources, 230);
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        painter.drawBounds();
        painter.drawPorts();

        if (painter.getShowState()) {
            final var bds = painter.getBounds();
            final var graphics = (Graphics2D) painter.getGraphics();
            final var posX = bds.getX();
            final var posY = bds.getY();

            Font font = new Font("SansSerif", Font.BOLD, 16);
            GraphicsUtil.drawText(graphics, font,"PLIC", posX+shift+25, posY+118,0,0, Color.black, Color.WHITE);
        }
    }

    @Override
    protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
        if (attr == ATTR_NUM_SOURCES) {
            instance.recomputeBounds();
            computePorts(instance);
        }
    }

    @Override
    public void propagate(InstanceState state) {
        // Get the number of interrupt sources from the instance's attributes
        int numSources = state.getAttributeValue(ATTR_NUM_SOURCES);

        final var plic = PLICRegisters.get(state, numSources);

        // Handle reset
        if (state.getPortValue(RESET) == Value.TRUE) {
            for (int i = 0; i < numSources; i++) {
                plic.setPriority(i, 0);
            }
            state.setPort(INTERRUPT_OUT, HiZ32, 9);
            return;
        }

        // Handle clock
        final var trigger = plic.updateClock(state.getPortValue(CLOCK));

        // Handle LOAD operation
        if (state.getPortValue(LOAD) == Value.TRUE) {
            int address = (int) state.getPortValue(ADDRESS).toLongValue();
            if (address - state.getAttributeValue(RISCV_PLIC_ADDR) >= 0
                && address - state.getAttributeValue(RISCV_PLIC_ADDR) < (numSources - 1) * 4L) {
                state.setPort(DATA_OUT, Value.createKnown(32, plic.getPriority(address)), 9);
            } else {
                state.setPort(DATA_OUT, HiZ32, 9); // Handle invalid address
            }
        } else {
            state.setPort(DATA_OUT, HiZ32, 9);
        }

        // Handle STORE operation
        if (state.getPortValue(STORE) == Value.TRUE && trigger) {
            int address = (int) state.getPortValue(ADDRESS).toLongValue();
            long dataIn = state.getPortValue(DATA_IN).toLongValue();

            if (address - state.getAttributeValue(RISCV_PLIC_ADDR) >= 0
                    && address - state.getAttributeValue(RISCV_PLIC_ADDR) < (numSources - 1) * 4L) {
                plic.setPriority((int) ((address - state.getAttributeValue(RISCV_PLIC_ADDR) ) / 4), dataIn & 0xFFFFFFFFL);
            }


        }

        // Update pending registers based on interrupt source inputs
        for (int i = 0; i < numSources; i++) {
            if (state.getPortValue(i + 8) == Value.TRUE) {  // Start checking ports after the first 7 control ports
                plic.setPending(i);
            }
        }

        for(int i = 0; i < numSources; i++) {
            if (plic.isPending(i) && plic.getClaimRegisters()[0] == 0) {
                state.setPort(INTERRUPT_OUT, Value.TRUE, 9);
                break;
            }
        }
    }
}
