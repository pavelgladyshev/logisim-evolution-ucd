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
import com.cburch.logisim.util.LineBuffer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Timer on an FPGA, in Verilog: the RISC-V machine timer, mtime and mtimecmp of 64 bits each, doing at each
 * tick what the component does at a rising clock edge. The module that does it, riscvTimer, is shipped with
 * Logisim (resources/logisim/hdl/riscv) and states the rules, which are TimerRegisters'; the module generated
 * here gives it the two addresses of the component's attributes and the reset value of mtimecmp.
 */
public class TimerHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  private static final String MODULE = "/resources/logisim/hdl/riscv/riscvTimer.v";

  private static final String MTIMECMP_STRING = "mtimecmpAddr";
  private static final int MTIMECMP_ID = -1;
  private static final String MTIME_STRING = "mtimeAddr";
  private static final int MTIME_ID = -2;

  public TimerHdlGeneratorFactory() {
    super();
    myParametersList
        .add(MTIMECMP_STRING, MTIMECMP_ID, HdlParameters.MAP_CONSTANT, 0)
        .add(MTIME_STRING, MTIME_ID, HdlParameters.MAP_CONSTANT, 0);
    myPorts
        .add(Port.CLOCK, "clock", 1, Timer.CLOCK)
        .add(Port.INPUT, "address", 32, Timer.ADDRESS, true)
        .add(Port.INPUT, "re", 1, Timer.READ_ENABLE, true)
        .add(Port.INPUT, "we", 1, Timer.WRITE_ENABLE, true)
        .add(Port.INPUT, "dataIn", 32, Timer.DATA_IN, true)
        .add(Port.INPUT, "reset", 1, Timer.RESET, true)
        .add(Port.OUTPUT, "dataOut", 32, Timer.DATA_OUT)
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
        .pair("mtimecmpReset", String.format("64'h%016x", TimerRegisters.MTIMECMP_RESET))
        .add("""
            riscvTimer #(.MTIMECMP_ADDR({{mtimecmp}}), .MTIME_ADDR({{mtime}}), .MTIMECMP_RESET({{mtimecmpReset}})) timer (
               .clock(clock), .tick(tick), .reset(reset), .address(address), .re(re), .we(we), .dataIn(dataIn),
               .dataOut(dataOut), .irq(irq));
            """);
  }

  @Override
  public List<String> getArchitecture(Netlist theNetlist, AttributeSet attrs, String componentName) {
    final var contents = new ArrayList<>(super.getArchitecture(theNetlist, attrs, componentName));
    contents.add("");
    try (final var in = TimerHdlGeneratorFactory.class.getResourceAsStream(MODULE);
         final var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      reader.lines().forEach(contents::add);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return contents;
  }

  @Override
  protected boolean isEdgeTriggered(AttributeSet attrs) {
    return true;   // the global clock and the tick, like the CPU and the Block Storage
  }

  @Override
  public boolean isHdlSupportedTarget(AttributeSet attrs) {
    // the riscv components go onto an FPGA in Verilog
    return getUnsupportedReasons(attrs).isEmpty();
  }

  @Override
  public List<String> getUnsupportedReasons(AttributeSet attrs) {
    return Hdl.isVerilog() ? List.of() : List.of(Hdl.verilogOnlyReason());
  }
}
