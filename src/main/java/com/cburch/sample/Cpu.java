package com.cburch.sample;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;

public class Cpu extends AbstractCpu {

    private static final PortInfo[] portInfos = new PortInfo[]{
            PortInfo.simpleInput("-", 1),
            PortInfo.simpleInput("SAddr", 32),
            PortInfo.simpleInput("RST", 1),
            PortInfo.simpleInput("DaIn", 32),
            PortInfo.simpleOutput("Pc", 32),
            PortInfo.simpleInput("Sync", 32),
            PortInfo.simpleInput("MW", 1),
            PortInfo.simpleInput("MR", 1),
            PortInfo.simpleOutput("DaOut", 32),
            PortInfo.simpleOutput("Addr", 32)
    };

    public Cpu() {
        super("cpu");
        pc = new Pc(Value.FALSE, Value.createKnown(32, 4194304));
        setOffsetBounds(Bounds.create(-80, -60, 160, 120));
        addStandardPins(portInfos, -80, 80, -30, 40, 20, 5);
    }

    @Override
    protected boolean getRESB(InstanceState state) {
        return false;
    }

    @Override
    protected boolean getPHI2(InstanceState state) {
        return false;
    }

    @Override
    public boolean getIRQB(InstanceState state) {
        return false;
    }

    @Override
    public boolean getNMIB(InstanceState state) {
        return false;
    }

    @Override
    public void doRead(InstanceState state, short address) {

    }

    @Override
    public void doWrite(InstanceState state, short address, byte data) {

    }

    @Override
    public byte getD(InstanceState state) {
        return 0;
    }

    @Override
    public void propagate(InstanceState state) {
        final var cur = Pc.get(state, Pc.getBitWidth());
        final var trigger = cur.updateClock(state.getPortValue(0));
        if(trigger) {
            cur.setValue(readPC());
            ///pc.execute???
            pc.updatePc();
            System.out.println(cur.getValue());
        }
        state.setPort(4, cur.getValue(), 9);
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        painter.drawBounds();
        int index = 0;

        for (int i = 0; i < portInfos.length; ++i) {
            if (portInfos[i] != null) {
                Direction direction = i < 5 ? Direction.EAST : Direction.WEST;
                if (i == 0) {
                    painter.drawClock(index, direction);
                } else {
                    painter.drawPort(index, portInfos[i].name, direction);
                }
                ++index;
            }
        }

        super.paintInstance(painter);
    }
}
