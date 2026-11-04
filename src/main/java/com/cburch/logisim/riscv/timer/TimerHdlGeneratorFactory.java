/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.timer;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.fpga.hdlgenerator.HdlParameters;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.riscv.DataBus;
import com.cburch.logisim.util.LineBuffer;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Timer on an FPGA, in Verilog and with a separate data bus only, doing at each tick what the component does
 * at a rising clock edge: a write to mtimecmp or mtime, then mtime counting on (31 bits), then the interrupt
 * request set when mtime reaches mtimecmp. Its registers are read without a clock, on Data Out; Reset holds mtime
 * at 0 and mtimecmp at 0xffffffff.
 */
public class TimerHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  private static final String MTIMECMP_STRING = "mtimecmpAddr";
  private static final int MTIMECMP_ID = -1;
  private static final String MTIME_STRING = "mtimeAddr";
  private static final int MTIME_ID = -2;

  public TimerHdlGeneratorFactory() {
    super();
    myParametersList
        .add(MTIMECMP_STRING, MTIMECMP_ID, HdlParameters.MAP_CONSTANT, 0)
        .add(MTIME_STRING, MTIME_ID, HdlParameters.MAP_CONSTANT, 0);
    myWires
        .addWire("s_counterBase", 31)
        .addWire("s_compareBase", 32)
        .addWire("s_time", 32)
        .addWire("s_nextCounter", 31)
        .addWire("s_nextCompare", 32)
        .addRegister("s_counter", 31)
        .addRegister("s_compare", 32)
        .addRegister("s_irq", 1);
    myPorts
        .add(Port.CLOCK, "clock", 1, Timer.CLOCK)
        .add(Port.INPUT, "address", 32, Timer.ADDRESS, true)
        .add(Port.INPUT, "re", 1, Timer.READ_ENABLE, true)
        .add(Port.INPUT, "we", 1, Timer.WRITE_ENABLE, true)
        .add(Port.INPUT, "dataIn", 32, Timer.DATA_IN, true)
        .add(Port.INPUT, "reset", 1, Timer.RESET, true)
        .add(Port.OUTPUT, "dataOut", 32, Timer.DATA)
        .add(Port.OUTPUT, "irq", 1, Timer.INTERRUPT_OUT);
  }

  @Override
  protected Map<String, String> getParameterMap(Netlist nets, netlistComponent componentInfo) {
    final var map = new TreeMap<>(super.getParameterMap(nets, componentInfo));
    final var attrs = componentInfo.getComponent().getAttributeSet();
    map.put(MTIMECMP_STRING, String.format("32'h%08x", attrs.getValue(Timer.RISCV_MTIMECMP_ADDR) & 0xffffffffL));
    map.put(MTIME_STRING, String.format("32'h%08x", attrs.getValue(Timer.RISCV_MTIME_ADDR) & 0xffffffffL));
    return map;
  }

  @Override
  public LineBuffer getModuleFunctionality(Netlist theNetlist, AttributeSet attrs) {
    return LineBuffer.getHdlBuffer()
        .pair("mtimecmp", MTIMECMP_STRING)
        .pair("mtime", MTIME_STRING)
        .add("""
            // what the component holds at a rising edge: Reset sets mtime to 0 and mtimecmp to 0xffffffff first
            assign s_counterBase = reset ? 31'd0 : s_counter;
            assign s_compareBase = reset ? 32'hffffffff : s_compare;
            // then a write, then mtime counts on, masked to 31 bits
            assign s_time        = (we && (address == {{mtime}})) ? dataIn : {1'b0, s_counterBase};
            assign s_nextCounter = s_time[30:0] + 31'd1;
            assign s_nextCompare = (we && (address == {{mtimecmp}})) ? dataIn : s_compareBase;

            assign dataOut = ~re ? 32'd0 :
                             (address == {{mtimecmp}}) ? s_compare :
                             (address == {{mtime}}) ? {1'b0, s_counter} : 32'd0;
            assign irq     = s_irq;

            initial
            begin
               s_counter = 0;
               s_compare = 0;
               s_irq     = 1'b0;
            end

            always @(posedge clock)
            begin
               // Reset holds mtime and mtimecmp between edges too, as the component does
               if (reset)
               begin
                  s_counter <= 31'd0;
                  s_compare <= 32'hffffffff;
               end
               else if (tick)
               begin
                  s_counter <= s_nextCounter;
                  s_compare <= s_nextCompare;
               end
               if (tick)
                  s_irq <= ({1'b0, s_nextCounter} >= s_nextCompare);
            end
            """);
  }

  @Override
  protected boolean isEdgeTriggered(AttributeSet attrs) {
    return true;   // the global clock and the tick, like the CPU and the Block Storage
  }

  @Override
  public boolean isHdlSupportedTarget(AttributeSet attrs) {
    // the riscv components go onto an FPGA in Verilog, and only on a separate data bus
    return Hdl.isVerilog() && DataBus.isSeparate(attrs);
  }
}
