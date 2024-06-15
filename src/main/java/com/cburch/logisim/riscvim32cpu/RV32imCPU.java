package com.cburch.logisim.riscvim32cpu;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.*;

public class RV32imCPU extends InstanceFactory {

    public static final String _ID = "RV32imCPU";

    RV32imCPU(){
        super(_ID);
        setOffsetBounds(Bounds.create(-70,-15,70,150));
        setPorts(new Port[]{
                new Port(-70, 0, Port.INPUT, 1),   //CLK IN (0)
                new Port(-70, 50, Port.INPUT, 1),  //RST (1)
                new Port(-70,100, Port.INPUT, 32), //DATA IN (2)
                new Port(0, 0, Port.OUTPUT, 32),   //ADDRESS (3)
                new Port(0, 20, Port.OUTPUT, 32),  //DATA OUT (4)
                new Port(0, 50, Port.OUTPUT, 1),   //MEMORY READ (5)
                new Port(0, 70, Port.OUTPUT, 1),   //MEMORY WRITE (6)
        });
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        painter.drawBounds();
        painter.drawClock(0, Direction.EAST);
        painter.drawPort(1,"RST", Direction.EAST);
        painter.drawPort(2,"DIN", Direction.EAST);
        painter.drawPort(3,"ADR", Direction.WEST);
        painter.drawPort(4,"DOUT", Direction.WEST);
        painter.drawPort(5,"MR", Direction.WEST);
        painter.drawPort(6,"MW", Direction.WEST);

    }

    @Override
    public void propagate(InstanceState state) {
        final var cur = RV32imCPUState.get(state);

        final var trigger = cur.updateClock(state.getPortValue(0));
        if (trigger) {
            //DO SOMETHING
        }
        //SET PORTS
        //state.setPort(1, cur.getValue(), 9);
    }
}
