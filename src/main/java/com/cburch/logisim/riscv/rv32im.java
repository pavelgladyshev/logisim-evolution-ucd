/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv;

import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.*;

import static com.cburch.logisim.riscv.CpuDrawSupport.*;
import static com.cburch.logisim.std.Strings.S;

/**
 * Initial stab at RISC-V rv32im cpu component. All of the code relevant to state, though,
 * appears in rv32imData class.
 */
class rv32im extends InstanceFactory {
  /**
   * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
   * prevent project files from loading.
   *
   * <p>Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "rv_32_im";

  public static final int CLOCK = 0;
  public static final int RESET = 1;
  public static final int DATA_IN = 2;
  public static final int ADDRESS = 3;
  public static final int DATA_OUT = 4;
  public static final int MEMREAD = 5;
  public static final int MEMWRITE = 6;
  public static final int SYNC = 7;
  public static final int CONTINUE = 8;

  public static final Attribute<Long> ATTR_RESET_ADDR =
          Attributes.forHexLong("resetAddress", S.getter("rv32imResetAddress"));

  static final Attribute<Boolean> ATTR_HEX_REGS =
          Attributes.forBoolean("hexRegisters", S.getter("rv32imHexRegisters"));

  // We don't have any instance variables related to an
  // individual instance's state. We can't put that here, because only one
  // rv32im object is ever created, and its job is to manage all
  // instances that appear in any circuits.

  public rv32im() {
    super(_ID);
    setOffsetBounds(Bounds.create(-60, -20, 180, 675));

    Port ps[] = new Port[9];

    ps[CLOCK] = new Port(-60, -10, Port.INPUT, 1);
    ps[RESET] = new Port(-60, 60, Port.INPUT, 1);
    ps[DATA_IN] = new Port(-60, 100, Port.INPUT, 32);
    ps[ADDRESS] = new Port(120, 0, Port.OUTPUT, 32);
    ps[DATA_OUT] = new Port(120, 30, Port.OUTPUT, 32);
    ps[MEMREAD] = new Port(120, 60, Port.OUTPUT, 1);
    ps[MEMWRITE] = new Port(120, 90, Port.OUTPUT, 1);
    ps[SYNC] = new Port(120, 120, Port.INPUT, 1);
    ps[CONTINUE] = new Port(120, 140, Port.INPUT, 1);

    ps[CLOCK].setToolTip(S.getter("rv32imClock"));
    ps[RESET].setToolTip(S.getter("rv32imReset"));
    ps[DATA_IN].setToolTip(S.getter("rv32imDataIn"));
    ps[ADDRESS].setToolTip(S.getter("rv32imAddress"));
    ps[DATA_OUT].setToolTip(S.getter("rv32imDataOut"));
    ps[MEMREAD].setToolTip(S.getter("rv32imMemRead"));
    ps[MEMWRITE].setToolTip(S.getter("rv32imMemWrite"));
    ps[SYNC].setToolTip(S.getter("rv32imSynchronizer"));
    ps[CONTINUE].setToolTip(S.getter("rv32imContinue"));

    setPorts(ps);

    // Add attributes
    setAttributes(
            new Attribute[] {ATTR_RESET_ADDR, ATTR_HEX_REGS, StdAttr.LABEL, StdAttr.LABEL_FONT},
            new Object[] {Long.valueOf(0), false, "", StdAttr.DEFAULT_LABEL_FONT});
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
      drawHexReg(graphics, posX, posY-80, false, (int) state.getOutputData().toLongValue(), "OUTPUT", true);
      drawHexReg(graphics, posX+80, posY-80, false, (int) state.getAddress().toLongValue(), "Addr", true);
      drawRegisters(graphics, posX, posY, false, state, painter.getAttributeValue(ATTR_HEX_REGS));
      drawCpuState(graphics, posX+80, posY-40, false, "CPU state", state.getCpuState());
    }
  }

  @Override
  public void propagate(InstanceState state) {

    // Checks reset port. If active -> clears out component data
    checkReset(state);

    // This helper method will end up creating a rv32imData object if one doesn't already exist.
    final var cur = rv32imData.get(state);

    // Check if continue button is pressed and mark flag to change CPU state on rising edge
    // of clock cycle
    checkContinuePressed(state, cur);

    // Check if intermixing data before 2nd clock cycle and
    // store intermix data when needed
    checkIntermixData(state, cur);

    // Check if clock signal is changing from low/false to high/true
    final var trigger = cur.updateClock(state.getPortValue(0));

    if (trigger) {

        if (cur.getPressedContinue()) {
          resumeCPU(cur);
          return;
        }

        if (cur.getIntermixFlag()) {
          // 2nd clock cycle finishes intermixing:
          // fetches new data, updates PC
          finishIntermixing(cur);
        } else {
          // process state update, current values of input ports (e.g Data-In bus value)
          // are passed to update() as parameters
          cur.update(state.getPortValue(DATA_IN).toLongValue());
        }

    }

    updatePorts(state, cur);

  }

  /** Helper functions */
  private void checkReset(InstanceState state) {
    if (state.getPortValue(RESET) == Value.TRUE) {
      state.setData(null);
    }
  }

  private void checkContinuePressed(InstanceState state, rv32imData cur) {
    if (cur.getCpuState() == rv32imData.CPUState.HALTED) {
      if (state.getPortValue(CONTINUE) == Value.TRUE) {
        cur.setPressedContinue(true);
        cur.setIsSync(Value.TRUE);
      }
    }
  }

  private void checkIntermixData(InstanceState state, rv32imData cur) {
    if(cur.getIntermixFlag()) {
      StoreInstruction.storeIntermixedData(cur, state.getPortValue(DATA_IN).toLongValue());
    }
  }

  // CALL THIS METHOD ON THE RISING EDGE OF THE CLOCK ONLY!
  private void finishIntermixing(rv32imData cur) {
    cur.getPC().increment();
    cur.fetchNextInstruction();
    cur.setIntermixFlag(false);
  }

  private void updatePorts(InstanceState state, rv32imData cur) {
    state.setPort(ADDRESS, cur.getAddress(), 9);
    state.setPort(DATA_OUT, cur.getOutputData(), 9);
    state.setPort(MEMREAD, cur.getMemRead(), 9);
    state.setPort(MEMWRITE, cur.getMemWrite(), 9);
    state.setPort(SYNC, cur.getIsSync(), 9);
  }

  private void resumeCPU(rv32imData cur) {
    cur.setPressedContinue(false);
    cur.setCpuState(rv32imData.CPUState.OPERATIONAL);
    cur.skipInstruction();
  }
}
