/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.riscv.DataBus;
import com.cburch.logisim.util.LineBuffer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The RV32IM CPU on an FPGA, in Verilog and with a separate data bus: riscvCpu (resources/logisim/hdl/riscv), a
 * model of the Java CPU that does at each rising edge of its clock what the Java CPU does there, and says how. It
 * needs a TLB of at most 32 entries, and has no instruction cache and no GDB server; and a clock cycle of 23 FPGA
 * clocks at least (getMinimumFpgaClocksPerCycle: with the Tang Nano 20K's 27 MHz and two ticks a cycle, Logisim's
 * ticks of 2.25 MHz and slower, such as 2048 kHz).
 */
public class rv32imHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  private static final String MODULE = "/resources/logisim/hdl/riscv/riscvCpu.v";

  /** FPGA clocks after a tick at which the CPU takes its inputs: when every device has answered. */
  private static final int START = 8;

  /**
   * The FPGA clocks of riscvCpu's longest step, one for each of its sequencer's steps: a division
   * (the edge, the interrupts, the instruction, 8 of the division, its result) whose next fetch
   * faults (the fetch, the handler's fetch).
   */
  private static final int LONGEST_STEP = 14;

  public static final int MAX_TLB_ENTRIES = 32;

  public rv32imHdlGeneratorFactory() {
    super();
    myWires.addWire("s_be", 4);
    myPorts
        .add(Port.CLOCK, "clock", 1, rv32im.CLOCK)
        .add(Port.INPUT, "reset", 1, rv32im.RESET, true)
        .add(Port.INPUT, "dataIn", 32, rv32im.DATA_IN, true)
        .add(Port.INPUT, "timerIrq", 1, rv32im.TIMER_INTERRUPT_REQUEST, true)
        .add(Port.INPUT, "extIrq", 1, rv32im.EXTERNAL_INTERRUPT_REQUEST, true)
        .add(Port.INPUT, "busReq", 1, rv32im.BUS_REQUEST, true)
        .add(Port.OUTPUT, "address", 32, rv32im.ADDRESS)
        .add(Port.OUTPUT, "dataOut", 32, rv32im.DATA)
        .add(Port.OUTPUT, "memRead", 1, rv32im.MEMREAD)
        .add(Port.OUTPUT, "memWrite", 1, rv32im.MEMWRITE)
        .add(Port.OUTPUT, "busAck", 1, rv32im.BUS_ACK)
        .add(Port.OUTPUT, "be0", 1, rv32im.BE0)
        .add(Port.OUTPUT, "be1", 1, rv32im.BE1)
        .add(Port.OUTPUT, "be2", 1, rv32im.BE2)
        .add(Port.OUTPUT, "be3", 1, rv32im.BE3);
  }

  @Override
  public LineBuffer getModuleFunctionality(Netlist theNetlist, AttributeSet attrs) {
    return LineBuffer.getHdlBuffer()
        .pair("resetAddress", String.format("32'h%08x", attrs.getValue(rv32im.ATTR_RESET_ADDR) & 0xffffffffL))
        .pair("tlbEntries", rv32im.tlbEntries(attrs))
        .pair("start", START)
        .add("""
            assign {be3, be2, be1, be0} = s_be;

            riscvCpu #(.RESET_ADDR({{resetAddress}}), .TLB_ENTRIES({{tlbEntries}}), .START({{start}})) cpu (
               .clock(clock), .tick(tick), .reset(reset), .dataIn(dataIn), .timerIrq(timerIrq), .extIrq(extIrq),
               .busReq(busReq), .address(address), .dataOut(dataOut), .memRead(memRead), .memWrite(memWrite),
               .be(s_be), .busAck(busAck), .late());
            """);
  }

  @Override
  public List<String> getArchitecture(Netlist theNetlist, AttributeSet attrs, String componentName) {
    final var contents = new ArrayList<>(super.getArchitecture(theNetlist, attrs, componentName));
    contents.add("");
    try (final var in = rv32imHdlGeneratorFactory.class.getResourceAsStream(MODULE);
         final var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      reader.lines().forEach(contents::add);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return contents;
  }

  @Override
  protected boolean isEdgeTriggered(AttributeSet attrs) {
    return true;   // the global clock and the tick, like the Block Storage and the Timer
  }

  @Override
  public boolean isHdlSupportedTarget(AttributeSet attrs) {
    return getUnsupportedReasons(attrs).isEmpty();
  }

  @Override
  public List<String> getUnsupportedReasons(AttributeSet attrs) {
    final var reasons = DataBus.unsupportedReasons(attrs);
    if (rv32im.tlbEntries(attrs) > MAX_TLB_ENTRIES) {
      reasons.add(Hdl.atMostReason(rv32im.ATTR_TLB_ENTRIES, MAX_TLB_ENTRIES));
    }
    if (attrs.getValue(rv32im.ATTR_INSTRUCTION_CACHE)) {
      reasons.add(Hdl.setAttributeReason(rv32im.ATTR_INSTRUCTION_CACHE, false));
    }
    if (attrs.getValue(rv32im.ATTR_GDB_SERVER_RUNNING)) {
      reasons.add(Hdl.setAttributeReason(rv32im.ATTR_GDB_SERVER_RUNNING, false));
    }
    return reasons;
  }

  /** The inputs at START, then the longest step, and the next tick one FPGA clock after it. */
  @Override
  public int getMinimumFpgaClocksPerCycle(AttributeSet attrs) {
    return START + LONGEST_STEP + 1;
  }
}
