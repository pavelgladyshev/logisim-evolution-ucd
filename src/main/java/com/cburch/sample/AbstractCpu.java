package com.cburch.sample;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;

public abstract class AbstractCpu extends InstanceFactory {
    protected Pc pc;

    public AbstractCpu(String name) {
        super(name);
    }

    public void writePC(Pc nv) {
        pc = nv;
    }

    public Value readPC() {
        return pc.getValue();
    }

    protected abstract boolean getRESB(InstanceState state);

    protected abstract boolean getPHI2(InstanceState state);

    public boolean getRDY(InstanceState state) {
        return true;
    }

    public boolean getSOB(InstanceState state) {
        return false;
    }

    public abstract boolean getIRQB(InstanceState state);

    public abstract boolean getNMIB(InstanceState state);

    public abstract void doRead(InstanceState state, short address);

    public abstract void doWrite(InstanceState state, short address, byte data);

    public abstract byte getD(InstanceState state);

    public void setRDY(InstanceState state, boolean value) {
    }

    public void setVPB(InstanceState state, boolean value) {
    }

    public void setSYNC(InstanceState state, boolean value) {
        System.out.println("SyncPSYCH");
    }

    public void setMLB(InstanceState state, boolean value) {
    }

    public void propagate(InstanceState state) {
        ///CoreState??
    }

    public void paintInstance(InstancePainter painter) {
        Graphics graphics = painter.getGraphics();
        if (graphics instanceof Graphics2D) {
            Font font = graphics.getFont();
            graphics.setFont(font.deriveFont(Font.BOLD));
            Bounds bounds = painter.getBounds();
            Graphics2D g2d = (Graphics2D) graphics;
            AffineTransform originalTransform = g2d.getTransform();
            AffineTransform newTransform = (AffineTransform) originalTransform.clone();
            newTransform.translate(bounds.getX() + bounds.getWidth() / 2, bounds.getY() + bounds.getHeight() / 2);
            if (bounds.getWidth() < bounds.getHeight()) {
                newTransform.quadrantRotate(-1, 0.0, 0.0);
            }
            g2d.setTransform(newTransform);
            g2d.rotate(Math.toRadians(90));
            GraphicsUtil.drawCenteredText(graphics, "RISC-V RV32IM", 0, 0);
            g2d.setTransform(originalTransform);
            graphics.setFont(font);
        }
    }

    protected void addStandardPins(PortInfo[] portInfos, int xEast, int xWest, int yStartEast, int yStartWest, int ySpacing, int numWest) {
        ArrayList<Port> ports = new ArrayList<>(portInfos.length);

        for (int i = 0; i < portInfos.length; i++) {
            final PortInfo portInfo = portInfos[i];
            if (portInfo != null) {
                boolean isWest = i >= numWest;
                int index = isWest ? i - numWest : i;
                Port port;
                if (isWest) {
                    port = new Port(xWest, yStartWest - index * ySpacing, portInfo.type, portInfo.bitWidth, portInfo.exclusive);
                } else {
                    port = new Port(xEast, yStartEast + index * ySpacing, portInfo.type, portInfo.bitWidth, portInfo.exclusive);
                }
                port.setToolTip(new StringGetter() {
                    public String toString() {
                        return portInfo.name;
                    }
                });
                ports.add(port);
            }
        }

        setPorts(ports);
    }
}
