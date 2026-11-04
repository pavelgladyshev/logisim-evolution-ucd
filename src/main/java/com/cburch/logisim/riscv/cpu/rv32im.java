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
import com.cburch.logisim.riscv.DataBus;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringUtil;

import java.awt.*;
import static com.cburch.logisim.riscv.cpu.CpuDrawSupport.*;
import static com.cburch.logisim.riscv.Strings.S;
import static com.cburch.logisim.riscv.blockstorage.BlockStorage.*;
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
  static final Value ZERO32 = Value.createKnown(32, 0);

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
  public static final int EXTERNAL_INTERRUPT_REQUEST = 7;
  public static final int BUS_REQUEST = 8;
  public static final int BUS_ACK = 9;
  public static final int BE0 = 10;
  public static final int BE1 = 11;
  public static final int BE2 = 12;
  public static final int BE3 = 13;
  public static final int DATA_IN = 14;   // with a separate data bus only: DATA is then Data Out


  public static final Attribute<Long> ATTR_RESET_ADDR =
          Attributes.forHexLong("resetAddress", S.getter("rv32imResetAddress"));

  public static final Attribute<Boolean> ATTR_HEX_REGS =
          Attributes.forBoolean("hexRegisters", S.getter("rv32imHexRegisters"));

  public static final Attribute<Integer> ATTR_TCP_PORT =
          Attributes.forIntegerRange("tcpPort", S.getter("rv32imTcpPort"), 0, 65535);

  public static final Attribute<Boolean> ATTR_GDB_SERVER_RUNNING =
          Attributes.forBoolean("gdbServerRunning", S.getter("rv32imStartGDBServer"));

  public static final Attribute<Boolean> ATTR_INSTRUCTION_CACHE =
          Attributes.forBoolean("instructionCache", S.getter("rv32imInstructionCache"));

  /** The TLB's entries: 128 by default; an FPGA takes fewer (TranslationLookasideBuffer). */
  public static final AttributeOption[] TLB_ENTRIES = {
          new AttributeOption(16, StringUtil.constantGetter("16")),
          new AttributeOption(32, StringUtil.constantGetter("32")),
          new AttributeOption(64, StringUtil.constantGetter("64")),
          new AttributeOption(128, StringUtil.constantGetter("128"))};

  public static final Attribute<AttributeOption> ATTR_TLB_ENTRIES =
          Attributes.forOption("tlbEntries", S.getter("rv32imTlbEntries"), TLB_ENTRIES);

  /** The number of TLB entries of a CPU with these attributes. */
  public static int tlbEntries(AttributeSet attrs) {
    final var option = attrs.getValue(ATTR_TLB_ENTRIES);
    return option == null ? TranslationLookasideBuffer.TLB_SIZE : (Integer) option.getValue();
  }

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
    setPorts(ports(false));

    // Add attributes
    setAttributes(
            new Attribute[] {
                    ATTR_RESET_ADDR, ATTR_HEX_REGS, ATTR_TCP_PORT, ATTR_GDB_SERVER_RUNNING, ATTR_INSTRUCTION_CACHE, ATTR_TLB_ENTRIES, ATTR_GDB_SERVER, DataBus.ATTR, StdAttr.LABEL, StdAttr.LABEL_FONT
            },
            new Object[] {Long.valueOf(0), false, 3333, false, false, TLB_ENTRIES[3], new GDBServer(), DataBus.BIDIRECTIONAL, "", StdAttr.DEFAULT_LABEL_FONT});
  }

  private static Port[] ports(boolean separateDataBus) {
    Port[] ps = new Port[separateDataBus ? 15 : 14];

    ps[CLOCK] = new Port(-60, -10, Port.INPUT, 1);
    ps[RESET] = new Port(-60, 60, Port.INPUT, 1);
    if (separateDataBus) {
      ps[DATA] = new Port(120, 30, Port.OUTPUT, 32);
      ps[DATA].setToolTip(S.getter("rv32imDataOut"));
      ps[DATA_IN] = new Port(-60, 30, Port.INPUT, 32);
      ps[DATA_IN].setToolTip(S.getter("rv32imDataIn"));
    } else {
      ps[DATA] = new Port(120, 30, Port.INOUT, 32);
      ps[DATA].setToolTip(S.getter("rv32imDataBus"));
    }
    ps[ADDRESS] = new Port(120, 0, Port.OUTPUT, 32);
    ps[MEMREAD] = new Port(120, 60, Port.OUTPUT, 1);
    ps[MEMWRITE] = new Port(120, 90, Port.OUTPUT, 1);
    ps[TIMER_INTERRUPT_REQUEST] = new Port(-60, 140, Port.INPUT, 1);
    ps[EXTERNAL_INTERRUPT_REQUEST] = new Port(-60, 190, Port.INPUT, 1);
    ps[BUS_REQUEST] = new Port(-60, 220, Port.INPUT, 1);
    ps[BUS_ACK] = new Port(120, 120, Port.OUTPUT, 1);
    ps[BE0] = new Port(120, 180, Port.OUTPUT, 1);
    ps[BE1] = new Port(120, 170, Port.OUTPUT, 1);
    ps[BE2] = new Port(120, 160, Port.OUTPUT, 1);
    ps[BE3] = new Port(120, 150, Port.OUTPUT, 1);



    ps[CLOCK].setToolTip(S.getter("rv32imClock"));
    ps[RESET].setToolTip(S.getter("rv32imReset"));
    ps[ADDRESS].setToolTip(S.getter("rv32imAddress"));
    ps[MEMREAD].setToolTip(S.getter("rv32imMemRead"));
    ps[MEMWRITE].setToolTip(S.getter("rv32imMemWrite"));
    ps[TIMER_INTERRUPT_REQUEST].setToolTip(S.getter("rv32imTimerInterruptRequestIn"));
    ps[EXTERNAL_INTERRUPT_REQUEST].setToolTip(S.getter("rv32imExternalInterruptRequestIn"));
    ps[BUS_REQUEST].setToolTip(S.getter("rv32imWaitRequestIn"));
    ps[BUS_ACK].setToolTip(S.getter("rv32imWaitAckOut"));
    ps[BE0].setToolTip(S.getter("rv32imByteEnable0"));
    ps[BE1].setToolTip(S.getter("rv32imByteEnable1"));
    ps[BE2].setToolTip(S.getter("rv32imByteEnable2"));
    ps[BE3].setToolTip(S.getter("rv32imByteEnable3"));

    return ps;
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    instance.setPorts(ports(DataBus.isSeparate(instance.getAttributeSet())));
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
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == DataBus.ATTR) {
      instance.setPorts(ports(DataBus.isSeparate(instance.getAttributeSet())));
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    painter.drawBounds();
    painter.drawLabel();
    painter.drawClock(0, Direction.EAST); // draw a triangle on port 0

    final var separate = DataBus.isSeparate(painter.getAttributeSet());
    for (int i = 1; i < (separate ? 15 : 14); i++) {
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
      final var dataPort = separate && state.getMemWrite() != Value.TRUE ? DATA_IN : DATA;
      drawHexReg(graphics, posX, posY-80, false,  (int) painter.getPortValue(dataPort).toLongValue(), "Data", true);
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
    Value dataIn = state.getPortValue(DataBus.isSeparate(state.getAttributeSet()) ? DATA_IN : DATA);
    Value timerInterrupt = state.getPortValue(TIMER_INTERRUPT_REQUEST);
    Value externalInterrupt = state.getPortValue(EXTERNAL_INTERRUPT_REQUEST);
    Value busRequest = state.getPortValue(BUS_REQUEST);


    if (reset == Value.TRUE) {
      long resetAddr = state.getAttributeValue(ATTR_RESET_ADDR);
      int port = state.getAttributeValue(ATTR_TCP_PORT);
      boolean gdbServerRunning = state.getAttributeValue(ATTR_GDB_SERVER_RUNNING);
      cpuState.reset(resetAddr, port, gdbServerRunning);
      cpuState.setCpuState(gdbServerRunning ? rv32imData.CPUState.STOPPED : rv32imData.CPUState.RUNNING);
      updateOutputSignals(state, cpuState);
      return;
    }

    boolean clockRisingEdge = cpuState.updateClock(clock) && clock == Value.TRUE;

    if (clockRisingEdge) {
      final long[] before = rv32imTrace.on() ? traceOutputs(cpuState) : null;
      clockEdge(cpuState, dataIn, timerInterrupt, externalInterrupt, busRequest);
      if (rv32imTrace.on()) {
        final int inFlags = (timerInterrupt == Value.TRUE ? 1 : 0) | (externalInterrupt == Value.TRUE ? 2 : 0)
            | (busRequest == Value.TRUE ? 4 : 0);
        // the word as the step takes it: an undefined one (a read where no device answers) is -1
        rv32imTrace.record(cpuState, before, dataIn.toLongValue() & 0xFFFFFFFFL, inFlags, traceOutputs(cpuState));
      }
    }

    updateOutputSignals(state, cpuState);
  }

  /**
   * A rising edge of the clock: the CPU grants the bus at an edge with Bus Request and releases it at the first edge
   * without, and does nothing else at those edges; at the others it executes a step (rv32imData.update).
   */
  private static void clockEdge(rv32imData cpuState, Value dataIn, Value timerInterrupt, Value externalInterrupt,
      Value busRequest) {
    if (cpuState.isBusGranted() && busRequest != Value.TRUE) {
      cpuState.setBusGranted(false);
      return;
    }
    if (!cpuState.isBusGranted() && busRequest == Value.TRUE) {
      cpuState.setBusGranted(true);
    }
    if (cpuState.isBusGranted()) return;
    long timerIrq = timerInterrupt == Value.TRUE ? 1 : 0;
    long externalIrq = externalInterrupt == Value.TRUE ? 1 : 0;
    long waitRequest = busRequest == Value.TRUE ? 1 : 0;
    long dataValue = dataIn.toLongValue();
    cpuState.update(dataValue, timerIrq, externalIrq, waitRequest);
  }

  /** What the CPU gives, as with a separate data bus, for rv32imTrace: address, data out and flags. */
  private long[] traceOutputs(rv32imData cpu) {
    if (cpu.isBusGranted()) return new long[] {0, 0, 1L << 6};
    final var memRead = cpu.getMemRead() == Value.TRUE;
    final var memWrite = cpu.getMemWrite() == Value.TRUE;
    var flags = (memRead ? 1 : 0) | (memWrite ? 2 : 0);
    final Value[] byteEnable = calculateByteEnables(cpu);
    for (var i = 0; i < 4; i++) {
      if (byteEnable[i] == Value.TRUE) flags |= 4 << i;
    }
    return new long[] {DataBus.known(cpu.getAddress()).toLongValue(),
        memWrite ? DataBus.known(cpu.getOutputData()).toLongValue() : 0, flags};
  }


  private void updateOutputSignals(InstanceState state, rv32imData cpu) {
    // A separate data bus has no floating outputs: where the bidirectional one lets go, it drives 0
    final var separate = DataBus.isSeparate(state.getAttributeSet());
    final var free32 = separate ? ZERO32 : HiZ32;
    final var free = separate ? Value.FALSE : HiZ;
    if (cpu.isBusGranted()) {
      state.setPort(ADDRESS, free32, 1);
      state.setPort(MEMREAD, free, 1);
      state.setPort(MEMWRITE, free, 1);
      state.setPort(DATA, free32, 1);
      state.setPort(BE0, free, 1);
      state.setPort(BE1, free, 1);
      state.setPort(BE2, free, 1);
      state.setPort(BE3, free, 1);
      state.setPort(BUS_ACK, Value.TRUE, 1);
    } else {
      state.setPort(ADDRESS, separate ? DataBus.known(cpu.getAddress()) : cpu.getAddress(), 1);
      state.setPort(MEMREAD, separate ? DataBus.known(cpu.getMemRead()) : cpu.getMemRead(), 1);
      state.setPort(MEMWRITE, separate ? DataBus.known(cpu.getMemWrite()) : cpu.getMemWrite(), 1);

      if (cpu.getMemWrite() == Value.TRUE) {
        state.setPort(DATA, separate ? DataBus.known(cpu.getOutputData()) : cpu.getOutputData(), 1);
      } else {
        state.setPort(DATA, free32, 1);
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
