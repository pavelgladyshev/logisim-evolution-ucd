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
import static com.cburch.logisim.riscv.cpu.HardDrive.*;
import static com.cburch.logisim.riscv.cpu.rv32imData.HiZ;

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

  // Cached byte enable patterns to avoid array allocation on every cycle
  private static final Value[] BE_NONE = {Value.FALSE, Value.FALSE, Value.FALSE, Value.FALSE};
  private static final Value[] BE_ALL = {Value.TRUE, Value.TRUE, Value.TRUE, Value.TRUE};
  private static final Value[] BE_BYTE_0 = {Value.TRUE, Value.FALSE, Value.FALSE, Value.FALSE};
  private static final Value[] BE_BYTE_1 = {Value.FALSE, Value.TRUE, Value.FALSE, Value.FALSE};
  private static final Value[] BE_BYTE_2 = {Value.FALSE, Value.FALSE, Value.TRUE, Value.FALSE};
  private static final Value[] BE_BYTE_3 = {Value.FALSE, Value.FALSE, Value.FALSE, Value.TRUE};
  private static final Value[] BE_HALF_0 = {Value.TRUE, Value.TRUE, Value.FALSE, Value.FALSE};
  private static final Value[] BE_HALF_2 = {Value.FALSE, Value.FALSE, Value.TRUE, Value.TRUE};

  public static final int CLOCK = 0;
  public static final int RESET = 1;
  public static final int DATA = 2;
  public static final int ADDRESS = 3;
  public static final int MEMREAD = 4;
  public static final int MEMWRITE = 5;
  public static final int TIMER_INTERRUPT_REQUEST = 6;
  public static final int PLIC_INTERRUPT_REQUEST = 7;
  public static final int BUS_REQUEST = 8;
  public static final int BUS_ACK = 9;
  public static final int BE0 = 10;
  public static final int BE1 = 11;
  public static final int BE2 = 12;
  public static final int BE3 = 13;


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

    Port[] ps = new Port[14];

    ps[CLOCK] = new Port(-60, -10, Port.INPUT, 1);
    ps[RESET] = new Port(-60, 60, Port.INPUT, 1);
    ps[DATA] = new Port(120, 30, Port.INOUT, 32);
    ps[ADDRESS] = new Port(120, 0, Port.OUTPUT, 32);
    ps[MEMREAD] = new Port(120, 60, Port.OUTPUT, 1);
    ps[MEMWRITE] = new Port(120, 90, Port.OUTPUT, 1);
    ps[TIMER_INTERRUPT_REQUEST] = new Port(-60, 140, Port.INPUT, 1);
    ps[PLIC_INTERRUPT_REQUEST] = new Port(-60, 190, Port.INPUT, 1);
    ps[BUS_REQUEST] = new Port(-60, 220, Port.INPUT, 1);
    ps[BUS_ACK] = new Port(120, 120, Port.OUTPUT, 1);
    ps[BE0] = new Port(120, 150, Port.OUTPUT, 1);
    ps[BE1] = new Port(120, 180, Port.OUTPUT, 1);
    ps[BE2] = new Port(120, 210, Port.OUTPUT, 1);
    ps[BE3] = new Port(120, 240, Port.OUTPUT, 1);



    ps[CLOCK].setToolTip(S.getter("rv32imClock"));
    ps[RESET].setToolTip(S.getter("rv32imReset"));
    ps[DATA].setToolTip(S.getter("rv32imDataBus"));
    ps[ADDRESS].setToolTip(S.getter("rv32imAddress"));
    ps[MEMREAD].setToolTip(S.getter("rv32imMemRead"));
    ps[MEMWRITE].setToolTip(S.getter("rv32imMemWrite"));
    ps[TIMER_INTERRUPT_REQUEST].setToolTip(S.getter("rv32imTimerInterruptRequestIn"));
    ps[PLIC_INTERRUPT_REQUEST].setToolTip(S.getter("rv32imPLICInterruptRequestIn"));
    ps[BUS_REQUEST].setToolTip(S.getter("rv32imWaitRequestIn"));
    ps[BUS_ACK].setToolTip(S.getter("rv32imWaitAckOut"));
    ps[BE0].setToolTip(S.getter("rv32imByteEnable0"));
    ps[BE1].setToolTip(S.getter("rv32imByteEnable1"));
    ps[BE2].setToolTip(S.getter("rv32imByteEnable2"));
    ps[BE3].setToolTip(S.getter("rv32imByteEnable3"));


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

    for (int i = 1; i < 14; i++) {
      painter.drawPort(i);
    }

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
      drawHexReg(graphics, posX, posY-80, false,  (int) painter.getPortValue(DATA).toLongValue(), "Data", true);
      drawHexReg(graphics, posX+80, posY-80, false, (int) state.getAddress().toLongValue(), "Addr", true);
      drawRegisters(graphics, posX, posY, false, state, painter.getAttributeValue(ATTR_HEX_REGS));
      drawCpuState(graphics, posX+80, posY-40, false, "CPU state", state.getCpuState());
    }
  }

  @Override
  public void propagate(InstanceState state) {

    rv32imData cpuState = rv32imData.get(state);


    Value clock = state.getPortValue(CLOCK);
    Value reset = state.getPortValue(RESET);
    Value dataIn = state.getPortValue(DATA);
    Value timerInterrupt = state.getPortValue(TIMER_INTERRUPT_REQUEST);
    Value plicInterrupt = state.getPortValue(PLIC_INTERRUPT_REQUEST);
    Value busRequest = state.getPortValue(BUS_REQUEST);


    if (reset == Value.TRUE) {
      long resetAddr = state.getAttributeValue(ATTR_RESET_ADDR);
      cpuState.reset(resetAddr);
      cpuState.setCpuState(rv32imData.CPUState.RUNNING);
      updateOutputSignals(state, cpuState);
      return;
    }

    boolean clockRisingEdge = cpuState.updateClock(clock) && clock == Value.TRUE;


    if (clockRisingEdge) {
      if (cpuState.isBusGranted() && busRequest != Value.TRUE) {
        cpuState.setBusGranted(false);
        updateOutputSignals(state, cpuState);
        return;
      }

      if (!cpuState.isBusGranted() && busRequest == Value.TRUE) {
        cpuState.setBusGranted(true);
     }


      //cpuState.setBusGranted(busRequest == Value.TRUE);


      if (cpuState.isBusGranted()) {
        updateOutputSignals(state, cpuState);
        return;
      }

      long timerIrq = timerInterrupt == Value.TRUE ? 1 : 0;
      long plicIrq = plicInterrupt == Value.TRUE ? 1 : 0;
      long waitRequest = busRequest == Value.TRUE ? 1 : 0;
      long dataValue = dataIn.toLongValue();
      cpuState.update(dataValue, timerIrq, plicIrq, 0);
    }

    updateOutputSignals(state, cpuState);
  }


  private void updateOutputSignals(InstanceState state, rv32imData cpu) {
    if (cpu.isBusGranted()) {
      state.setPort(ADDRESS, HiZ32, 1);
      state.setPort(MEMREAD, HiZ, 1);
      state.setPort(MEMWRITE, HiZ, 1);
      state.setPort(DATA, HiZ32, 1);
      state.setPort(BE0, HiZ, 1);
      state.setPort(BE1, HiZ, 1);
      state.setPort(BE2, HiZ, 1);
      state.setPort(BE3, HiZ, 1);
      state.setPort(BUS_ACK, Value.TRUE, 1);
    } else {
      state.setPort(ADDRESS, cpu.getAddress(), 1);
      state.setPort(MEMREAD, cpu.getMemRead(), 1);
      state.setPort(MEMWRITE, cpu.getMemWrite(), 1);

      if (cpu.getMemWrite() == Value.TRUE) {
        state.setPort(DATA, cpu.getOutputData(), 1);
      } else {
        state.setPort(DATA, HiZ32, 1);
      }

      Value[] byteEnable = calculateByteEnables(cpu);
      state.setPort(BE0, byteEnable[0], 1);
      state.setPort(BE1, byteEnable[1], 1);
      state.setPort(BE2, byteEnable[2], 1);
      state.setPort(BE3, byteEnable[3], 1);

      state.setPort(BUS_ACK, Value.FALSE, 1);
    }
  }

  private Value[] calculateByteEnables(rv32imData cpu) {
    if (!cpu.isMemoryAccessActive()) {
      return BE_NONE;
    }

    int width = cpu.getAccessWidth();
    if (width == 4) {
      return BE_ALL;
    }

    int offset = (int) (cpu.getAddress().toLongValue() & 0x03);
    if (width == 1) {
      return switch (offset) {
        case 0 -> BE_BYTE_0;
        case 1 -> BE_BYTE_1;
        case 2 -> BE_BYTE_2;
        default -> BE_BYTE_3;
      };
    } else if (width == 2) {
      return (offset == 0) ? BE_HALF_0 : BE_HALF_2;
    }
    return BE_NONE;
  }

}
