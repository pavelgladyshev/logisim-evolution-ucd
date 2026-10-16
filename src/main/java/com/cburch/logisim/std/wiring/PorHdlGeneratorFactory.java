/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.wiring;

import static com.cburch.logisim.std.Strings.S;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.gui.Reporter;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.fpga.hdlgenerator.HdlParameters;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.util.LineBuffer;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * On an FPGA the power-on reset gives its start value for its configured time after the FPGA has been
 * configured, as at the start of a simulation, and then its end value. It counts cycles of the FPGA clock,
 * taken from the clock tree of its circuit; in a circuit without clocked components there is no clock to
 * count, and the output is the end value from the start (with a warning).
 */
public class PorHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  private static final String NR_OF_CLOCKS_STRING = "nrOfClocks";
  private static final int NR_OF_CLOCKS_ID = -1;
  private static final String COUNT_BITS_STRING = "countBits";
  private static final int COUNT_BITS_ID = -2;
  private static final String START_VALUE_STRING = "startValue";
  private static final int START_VALUE_ID = -3;

  public PorHdlGeneratorFactory() {
    super();
    myParametersList
        .add(NR_OF_CLOCKS_STRING, NR_OF_CLOCKS_ID, HdlParameters.MAP_CONSTANT, 1)
        .add(COUNT_BITS_STRING, COUNT_BITS_ID, HdlParameters.MAP_CONSTANT, 1)
        .add(START_VALUE_STRING, START_VALUE_ID, HdlParameters.MAP_CONSTANT, 1);
    myWires
        .addWire("s_active", 1)
        .addRegister("s_count", COUNT_BITS_ID);
    myPorts
        .add(Port.INPUT, "globalClock", 1, "'0'")
        .add(Port.OUTPUT, "por", 1, 0);
  }

  @Override
  protected Map<String, String> getParameterMap(Netlist nets, netlistComponent componentInfo) {
    final var map = new TreeMap<>(super.getParameterMap(nets, componentInfo));
    final var attrs = componentInfo.getComponent().getAttributeSet();
    final var seconds = (Integer) attrs.getValue(attrs.getAttribute(PowerOnReset.DURATION_NAME));
    final var clocks = Math.max(1L, Math.min(Integer.MAX_VALUE, seconds * Hdl.getFpgaClockFrequency()));
    map.put(NR_OF_CLOCKS_STRING, Long.toString(clocks));
    map.put(COUNT_BITS_STRING, Integer.toString(64 - Long.numberOfLeadingZeros(clocks)));
    map.put(START_VALUE_STRING, PowerOnReset.startsHigh(attrs) ? "1" : "0");
    return map;
  }

  @Override
  public SortedMap<String, String> getPortMap(Netlist nets, Object mapInfo) {
    final var map = new TreeMap<String, String>(super.getPortMap(nets, mapInfo));
    if (mapInfo instanceof netlistComponent) {
      if (nets.numberOfClockTrees() > 0) {
        map.put("globalClock", LineBuffer.formatHdl("{{1}}0{{<}}{{2}}{{>}}", CLOCK_TREE_NAME,
            ClockHdlGeneratorFactory.GLOBAL_CLOCK_INDEX));
      } else {
        map.put("globalClock", Hdl.zeroBit());
        Reporter.report.addWarning(S.get("porNoClockOnFpga", nets.getCircuitName()));
      }
    }
    return map;
  }

  @Override
  public LineBuffer getModuleFunctionality(Netlist theNetlist, AttributeSet attrs) {
    final var contents = LineBuffer.getHdlBuffer()
        .pair("clocks", NR_OF_CLOCKS_STRING)
        .pair("bits", COUNT_BITS_STRING)
        .pair("start", START_VALUE_STRING);
    if (Hdl.isVhdl()) {
      contents.empty().addVhdlKeywords().add("""
          s_active <= '1' {{when}} unsigned(s_count) /= to_unsigned({{clocks}}, {{bits}}) {{else}} '0';
          por      <= s_active {{when}} {{start}} = 1 {{else}} {{not}}(s_active);

          counter : {{process}}(globalClock) {{is}}
          {{begin}}
             {{if}} (rising_edge(globalClock)) {{then}}
                {{if}} (s_count(0) /= '0' {{and}} s_count(0) /= '1') {{then}} -- simulation only
                   s_count <= ({{others}} => '0');
                {{elsif}} (s_active = '1') {{then}}
                   s_count <= std_logic_vector(unsigned(s_count) + 1);
                {{end}} {{if}};
             {{end}} {{if}};
          {{end}} {{process}} counter;
          """);
    } else {
      contents.add("""
          // counts FPGA clock cycles from the end of the configuration, when the registers are 0
          assign s_active = (s_count != {{clocks}}) ? 1'b1 : 1'b0;
          assign por      = ({{start}} == 1) ? s_active : ~s_active;

          initial s_count = 0;

          always @(posedge globalClock)
             if (s_active) s_count <= s_count + 1;
          """);
    }
    return contents.empty();
  }

  @Override
  public boolean isHdlSupportedTarget(AttributeSet attrs) {
    return true;
  }
}
