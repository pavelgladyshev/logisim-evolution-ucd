/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.blockstorage;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
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
 * The Block Storage on an FPGA, in Verilog: its registers and DMA as the component has them, and a microSD card in
 * SPI mode, on the four mappable pins SD_MISO, SD_SCK, SD_MOSI and SD_CS (on the Tang Nano 20K: TF_D0, TF_CLK,
 * TF_CMD and TF_D3), where the component has its image file. The module that does it, riscvSdBlockStorage, is
 * shipped with Logisim (resources/logisim/hdl/riscv) and says how it works.
 */
public class BlockStorageHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  private static final String MODULE = "/resources/logisim/hdl/riscv/riscvSdBlockStorage.v";

  public BlockStorageHdlGeneratorFactory() {
    super();
    myWires.addWire("s_be", 4);
    myPorts
        .add(Port.CLOCK, "clock", 1, BlockStorage.CLOCK)
        .add(Port.INPUT, "addr", 3, BlockStorage.ADDR, true)
        .add(Port.INPUT, "re", 1, BlockStorage.READ_ENABLE, true)
        .add(Port.INPUT, "we", 1, BlockStorage.WRITE_ENABLE, true)
        .add(Port.INPUT, "dataIn", 32, BlockStorage.DATA_IN, true)
        .add(Port.INPUT, "reset", 1, BlockStorage.RESET, true)
        .add(Port.INPUT, "busAck", 1, BlockStorage.BUS_ACK, true)
        .add(Port.INPUT, "memDataIn", 32, BlockStorage.MEM_DATA_IN, true)
        .add(Port.OUTPUT, "dataOut", 32, BlockStorage.DATA_OUT)
        .add(Port.OUTPUT, "busReq", 1, BlockStorage.BUS_REQUEST)
        .add(Port.OUTPUT, "memRead", 1, BlockStorage.MEM_READ)
        .add(Port.OUTPUT, "memWrite", 1, BlockStorage.MEM_WRITE)
        .add(Port.OUTPUT, "dmaAddr", 32, BlockStorage.DMA_ADDR)
        .add(Port.OUTPUT, "be0", 1, BlockStorage.BE0)
        .add(Port.OUTPUT, "be1", 1, BlockStorage.BE1)
        .add(Port.OUTPUT, "be2", 1, BlockStorage.BE2)
        .add(Port.OUTPUT, "be3", 1, BlockStorage.BE3)
        .add(Port.INPUT, "sdMiso", 1, "'1'")
        .add(Port.OUTPUT, "sdSck", 1, "open")
        .add(Port.OUTPUT, "sdMosi", 1, "open")
        .add(Port.OUTPUT, "sdCs", 1, "open");
  }

  @Override
  public Map<String, String> getPortMap(Netlist nets, Object mapInfo) {
    final var map = new TreeMap<String, String>(super.getPortMap(nets, mapInfo));
    if (mapInfo instanceof netlistComponent componentInfo) {
      map.put("sdMiso", bubble(LOCAL_INPUT_BUBBLE_BUS_NAME, componentInfo.getLocalBubbleInputStartId()));
      final var outputs = componentInfo.getLocalBubbleOutputStartId();
      map.put("sdSck", bubble(LOCAL_OUTPUT_BUBBLE_BUS_NAME, outputs));
      map.put("sdMosi", bubble(LOCAL_OUTPUT_BUBBLE_BUS_NAME, outputs + 1));
      map.put("sdCs", bubble(LOCAL_OUTPUT_BUBBLE_BUS_NAME, outputs + 2));
    }
    return map;
  }

  private static String bubble(String bus, int index) {
    return LineBuffer.formatHdl("{{1}}{{<}}{{2}}{{>}}", bus, index);
  }

  @Override
  public LineBuffer getModuleFunctionality(Netlist theNetlist, AttributeSet attrs) {
    final double clock = Hdl.getFpgaClockFrequency();
    return LineBuffer.getHdlBuffer()
        // the SPI clock: at most 400 kHz while the card starts, then at most 20 MHz; 2 ms after power-up first
        .pair("slow", (int) Math.min(255, Math.max(1, Math.ceil(clock / 800_000))))
        .pair("fast", (int) Math.min(255, Math.max(1, Math.ceil(clock / 40_000_000))))
        .pair("powerUp", (long) Math.min(1_000_000, Math.ceil(clock / 500)))
        .add("""
            assign {be3, be2, be1, be0} = s_be;

            riscvSdBlockStorage #(.SLOW_DIV({{slow}}), .FAST_DIV({{fast}}), .POWER_UP({{powerUp}})) card (
               .clock(clock), .tick(tick), .reset(reset), .addr(addr), .re(re), .we(we), .dataIn(dataIn),
               .dataOut(dataOut), .busReq(busReq), .busAck(busAck), .memRead(memRead), .memWrite(memWrite),
               .dmaAddr(dmaAddr), .be(s_be), .memDataIn(memDataIn),
               .sdSck(sdSck), .sdMosi(sdMosi), .sdCs(sdCs), .sdMiso(sdMiso));
            """);
  }

  @Override
  public List<String> getArchitecture(Netlist theNetlist, AttributeSet attrs, String componentName) {
    final var contents = new ArrayList<>(super.getArchitecture(theNetlist, attrs, componentName));
    contents.add("");
    try (final var in = BlockStorageHdlGeneratorFactory.class.getResourceAsStream(MODULE);
         final var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      reader.lines().forEach(contents::add);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return contents;
  }

  @Override
  protected boolean isEdgeTriggered(AttributeSet attrs) {
    return true;   // the global clock and the tick, like the CPU and the Timer
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
