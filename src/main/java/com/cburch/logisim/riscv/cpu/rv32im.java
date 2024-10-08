/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.*;

import static com.cburch.logisim.riscv.cpu.CpuDrawSupport.*;
import static com.cburch.logisim.riscv.Strings.S;

/**
 * Initial stab at RISC-V rv32im cpu component. All of the code relevant to state, though,
 * appears in rv32imData class.
 */
public class rv32im extends InstanceFactory {
  /**
   * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
   * prevent project files from loading.
   *
   * <p>Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "RV32IM CPU";

  static final Value HiZ32 = Value.createUnknown(BitWidth.create(32));

  public static final int CLOCK = 0;
  public static final int RESET = 1;
  public static final int DATA_IN = 2;
  public static final int ADDRESS = 3;
  public static final int DATA_OUT = 4;
  public static final int MEMREAD = 5;
  public static final int MEMWRITE = 6;
  public static final int TIMER_INTERRUPT_REQUEST = 7;
  public static final int PLIC_INTERRUPT_REQUEST = 8;

  public static final Attribute<Long> ATTR_RESET_ADDR =
          Attributes.forHexLong("resetAddress", S.getter("rv32imResetAddress"));

  public static final Attribute<Boolean> ATTR_HEX_REGS =
          Attributes.forBoolean("hexRegisters", S.getter("rv32imHexRegisters"));

  public static final Attribute<Integer> ATTR_TCP_PORT =
          Attributes.forIntegerRange("tcpPort", S.getter("rv32imTcpPort"), 0, 65535);

  public static final Attribute<Boolean> ATTR_GDB_SERVER_RUNNING =
          Attributes.forBoolean("gdbServerRunning", S.getter("rv32imStartGDBServer"));

  protected static class GDBServerAttribute extends Attribute<GDBServer> {
    @Override
    public GDBServer parse(String value) {
      return null;
    }

    @Override
    public boolean isToSave() {
      return false;
    }

    @Override
    public boolean isHidden() {
      return true;
    }
  }

  public static final Attribute<GDBServer> ATTR_GDB_SERVER = new GDBServerAttribute();



  public rv32im() {
    super(_ID);
    setOffsetBounds(Bounds.create(-60, -20, 180, 675));

    Port[] ps = new Port[9];

    ps[CLOCK] = new Port(-60, -10, Port.INPUT, 1);
    ps[RESET] = new Port(-60, 60, Port.INPUT, 1);
    ps[DATA_IN] = new Port(-60, 100, Port.INPUT, 32);
    ps[ADDRESS] = new Port(120, 0, Port.OUTPUT, 32);
    ps[DATA_OUT] = new Port(120, 30, Port.OUTPUT, 32);
    ps[MEMREAD] = new Port(120, 60, Port.OUTPUT, 1);
    ps[MEMWRITE] = new Port(120, 90, Port.OUTPUT, 1);
    ps[TIMER_INTERRUPT_REQUEST] = new Port(-60, 140, Port.INPUT, 1);
    ps[PLIC_INTERRUPT_REQUEST] = new Port(-60, 190, Port.INPUT, 1);

    ps[CLOCK].setToolTip(S.getter("rv32imClock"));
    ps[RESET].setToolTip(S.getter("rv32imReset"));
    ps[DATA_IN].setToolTip(S.getter("rv32imDataIn"));
    ps[ADDRESS].setToolTip(S.getter("rv32imAddress"));
    ps[DATA_OUT].setToolTip(S.getter("rv32imDataOut"));
    ps[MEMREAD].setToolTip(S.getter("rv32imMemRead"));
    ps[MEMWRITE].setToolTip(S.getter("rv32imMemWrite"));
    ps[TIMER_INTERRUPT_REQUEST].setToolTip(S.getter("rv32imTimerInterruptRequestIn"));
    ps[PLIC_INTERRUPT_REQUEST].setToolTip(S.getter("rv32imPLICInterruptRequestIn"));

    setPorts(ps);

    // Add attributes
    setAttributes(
            new Attribute[] {
                    ATTR_RESET_ADDR, ATTR_HEX_REGS, ATTR_TCP_PORT, ATTR_GDB_SERVER_RUNNING, ATTR_GDB_SERVER, StdAttr.LABEL, StdAttr.LABEL_FONT
            },
            new Object[] {Long.valueOf(0), false, 3333, false, new GDBServer(), "", StdAttr.DEFAULT_LABEL_FONT});
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    final var bds = instance.getBounds();
    instance.setTextField(
            StdAttr.LABEL,
            StdAttr.LABEL_FONT,
            bds.getX() + bds.getWidth() / 2,
            bds.getY() - 3,
            GraphicsUtil.H_CENTER,
            GraphicsUtil.V_BASELINE);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    painter.drawBounds();
    painter.drawLabel();
    painter.drawClock(0, Direction.EAST); // draw a triangle on port 0
    painter.drawPort(1); // draw port 1 as just a dot
    painter.drawPort(2); // draw port 2 as just a dot
    painter.drawPort(3); // draw port 3 as just a dot
    painter.drawPort(4); // draw port 4 as just a dot
    painter.drawPort(5); // draw port 5 as just a dot
    painter.drawPort(6); // draw port 6 as just a dot
    painter.drawPort(7); // draw port 7 as just a dot
    painter.drawPort(8); // draw port 8 as just a dot

    // Display the current state.
    // If the context says not to show state (as when generating
    // printer output), then skip this.
    if (painter.getShowState()) {
      final var state = rv32imData.get(painter);
      final var bds = painter.getBounds();
      final var graphics = (Graphics2D) painter.getGraphics();
      final var posX = bds.getX() + 10;
      final var posY = bds.getY() + 170;

      Font font = new Font("SansSerif", Font.BOLD, 20);
      GraphicsUtil.drawText(graphics, font,"RISC-V RV32IM", posX+80, posY-127,0,0, Color.black, Color.WHITE);

      drawHexReg(graphics, posX, posY - 40, false, (int) state.getPC().get(), "PC", true);
      drawHexReg(graphics, posX, posY-80, false, (int) state.getOutputData().toLongValue(), "Data Out", true);
      drawHexReg(graphics, posX+80, posY-80, false, (int) state.getAddress().toLongValue(), "Addr", true);
      drawRegisters(graphics, posX, posY, false, state, painter.getAttributeValue(ATTR_HEX_REGS));
      drawCpuState(graphics, posX+80, posY-40, false, "CPU state", state.getCpuState());
    }
  }

  @Override
  public void propagate(InstanceState state) {

    rv32imData cur;

    synchronized (rv32imData.class) {
      cur = (rv32imData) state.getData();
      if ((state.getPortValue(RESET) == Value.TRUE) || (null == cur)) {
        // destroy old data, if reset has been pressed
        state.setData(null);
        // if a gdb server is running, we need to stop it
        GDBServer gdbServer = (GDBServer) state.getAttributeValue(ATTR_GDB_SERVER);
        gdbServer.stopGDBServer();
        // getData() method will end up creating a new rv32imData object if state's data is null
        // and re-starting the GDBServer if required
        cur = rv32imData.get(state);
      }
    }

    // Check if clock signal is changing from low/false to high/true
    final var isClockRisingEdge = cur.updateClock(state.getPortValue(CLOCK));

    if (isClockRisingEdge) {
      // process state update, current values of input ports (e.g. Data-In bus value)
      // are passed to update() as parameters
      cur.update(state.getPortValue(DATA_IN).toLongValue(),
                 state.getPortValue(TIMER_INTERRUPT_REQUEST) == Value.TRUE ? 1 : 0,
                 state.getPortValue(PLIC_INTERRUPT_REQUEST) == Value.TRUE ? 1 : 0);
    }

    state.setPort(ADDRESS, cur.getAddress(), 9);

    if (cur.getOutputDataWidth() != 0) {
      // DATA_OUT = combination of rv32im data output with the data from DATA_IN
      state.setPort(DATA_OUT, cur.getInvertedOutputDataMask().controls(
              state.getPortValue(DATA_IN)).combine(cur.getOutputData()), 9);
    } else {
      state.setPort(DATA_OUT, HiZ32, 9);
    }
    state.setPort(MEMREAD, cur.getMemRead(), 9);
    state.setPort(MEMWRITE, cur.getMemWrite(), 9);
  }

}
