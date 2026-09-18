/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.arith;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.fpga.hdlgenerator.HdlParameters;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.LineBuffer;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * HDL for the Divider, with the same results as its simulation (Divider.computeResult):
 * <ul>
 *   <li>the dividend is {upper, inputA} when "upper" is connected, otherwise inputA extended
 *       (sign-extended in two's complement mode);</li>
 *   <li>division truncates towards zero and the remainder takes the sign of the dividend;</li>
 *   <li>a divisor of 0 is treated as 1 (quotient = dividend, remainder = 0);</li>
 *   <li>both results are truncated to the component's width.</li>
 * </ul>
 * Without "upper" the division is done in width+1 bits (enough for -2^(w-1) / -1), otherwise in 2*width bits.
 */
public class DividerHdlGeneratorFactory extends AbstractHdlGeneratorFactory {
  private static final String NR_OF_BITS_STRING = "nrOfBits";
  private static final int NR_OF_BITS_ID = -1;
  private static final String CALC_BITS_STRING = "calcBits";
  private static final int CALC_BITS_ID = -2;
  private static final String SIGNED_STRING = "signedDivider";
  private static final int SIGNED_ID = -3;
  private static final String HAS_UPPER_STRING = "hasUpper";
  private static final int HAS_UPPER_ID = -4;

  public DividerHdlGeneratorFactory() {
    super();
    myParametersList
        .add(NR_OF_BITS_STRING, NR_OF_BITS_ID)
        .add(CALC_BITS_STRING, CALC_BITS_ID, HdlParameters.MAP_OFFSET, 1)
        .add(SIGNED_STRING, SIGNED_ID, HdlParameters.MAP_ATTRIBUTE_OPTION, Comparator.MODE_ATTR,
            ComparatorHdlGeneratorFactory.SIGNED_MAP)
        .add(HAS_UPPER_STRING, HAS_UPPER_ID, HdlParameters.MAP_CONSTANT, 0);
    myWires
        .addWire("s_zeroDivisor", 1)
        .addWire("s_numU", CALC_BITS_ID)
        .addWire("s_numS", CALC_BITS_ID)
        .addWire("s_denU", CALC_BITS_ID)
        .addWire("s_denS", CALC_BITS_ID)
        .addWire("s_quot", CALC_BITS_ID)
        .addWire("s_rem", CALC_BITS_ID);
    myPorts
        .add(Port.INPUT, "inputA", NR_OF_BITS_ID, Divider.IN0)
        .add(Port.INPUT, "inputB", NR_OF_BITS_ID, Divider.IN1)
        .add(Port.INPUT, "upper", NR_OF_BITS_ID, Divider.UPPER)
        .add(Port.OUTPUT, "quotient", NR_OF_BITS_ID, Divider.OUT)
        .add(Port.OUTPUT, "remainder", NR_OF_BITS_ID, Divider.REM);
  }

  @Override
  protected Map<String, String> getParameterMap(Netlist nets, netlistComponent componentInfo) {
    final var map = new TreeMap<>(super.getParameterMap(nets, componentInfo));
    final var width = componentInfo.getComponent().getAttributeSet().getValue(StdAttr.WIDTH).getWidth();
    final var hasUpper = componentInfo.isEndConnected(Divider.UPPER);
    map.put(HAS_UPPER_STRING, hasUpper ? "1" : "0");
    map.put(CALC_BITS_STRING, Integer.toString(hasUpper ? 2 * width : width + 1));
    return map;
  }

  /**
   * In VHDL the data ports of a 1-bit divider are still vectors (0 downto 0), while its nets are
   * std_logic: such ports are connected element-wise (unconnected outputs stay "open").
   */
  @Override
  public SortedMap<String, String> getPortMap(Netlist nets, Object mapInfo) {
    final var map = new TreeMap<String, String>(super.getPortMap(nets, mapInfo));
    if (mapInfo instanceof netlistComponent comp && Hdl.isVhdl()
        && comp.getComponent().getAttributeSet().getValue(StdAttr.WIDTH).getWidth() == 1) {
      for (final var port : List.of("inputA", "inputB", "upper", "quotient", "remainder")) {
        final var net = map.get(port);
        if (net != null && !net.equalsIgnoreCase("open")) {
          map.remove(port);
          map.put(port + "(0)", net);
        }
      }
    }
    return map;
  }

  @Override
  public LineBuffer getModuleFunctionality(Netlist theNetlist, AttributeSet attrs) {
    final var contents = LineBuffer.getHdlBuffer()
            .pair("nrOfBits", NR_OF_BITS_STRING)
            .pair("calcBits", CALC_BITS_STRING)
            .pair("signed", SIGNED_STRING)
            .pair("hasUpper", HAS_UPPER_STRING);
    if (Hdl.isVhdl()) {
      contents.empty().addVhdlKeywords().add("""
          s_zeroDivisor <= '1' {{when}} unsigned(inputB) = 0 {{else}} '0';

          withUpper : {{if}} {{hasUpper}} = 1 {{generate}}
             s_numU <= upper & inputA;
             s_numS <= upper & inputA;
          {{end}} {{generate}} withUpper;

          withoutUpper : {{if}} {{hasUpper}} = 0 {{generate}}
             s_numU <= std_logic_vector(resize(unsigned(inputA), {{calcBits}}));
             s_numS <= std_logic_vector(resize(signed(inputA), {{calcBits}}));
          {{end}} {{generate}} withoutUpper;

          s_denU <= std_logic_vector(to_unsigned(1, {{calcBits}})) {{when}} s_zeroDivisor = '1' {{else}}
                    std_logic_vector(resize(unsigned(inputB), {{calcBits}}));
          s_denS <= std_logic_vector(to_signed(1, {{calcBits}})) {{when}} s_zeroDivisor = '1' {{else}}
                    std_logic_vector(resize(signed(inputB), {{calcBits}}));

          s_quot <= std_logic_vector(signed(s_numS) / signed(s_denS)) {{when}} {{signed}} = 1 {{else}}
                    std_logic_vector(unsigned(s_numU) / unsigned(s_denU));
          s_rem  <= std_logic_vector(signed(s_numS) {{rem}} signed(s_denS)) {{when}} {{signed}} = 1 {{else}}
                    std_logic_vector(unsigned(s_numU) {{rem}} unsigned(s_denU));

          quotient  <= s_quot({{nrOfBits}}-1 {{downto}} 0);
          remainder <= s_rem({{nrOfBits}}-1 {{downto}} 0);
          """);
    } else {
      contents.add("""
          // the signed results get their own wires: mixing them with unsigned operands in one expression
          // would make Verilog evaluate the division as unsigned
          wire signed [{{calcBits}}-1:0] s_quotS;
          wire signed [{{calcBits}}-1:0] s_remS;

          // (the spaces in "{ {" keep LineBuffer from reading the replications as placeholders)
          assign s_zeroDivisor = (inputB == 0) ? 1'b1 : 1'b0;
          assign s_numU = ({{hasUpper}} == 1) ? {upper, inputA} : { {{{nrOfBits}}{1'b0}}, inputA };
          assign s_numS = ({{hasUpper}} == 1) ? {upper, inputA} : { {{{nrOfBits}}{inputA[{{nrOfBits}}-1]}}, inputA };
          assign s_denU = (s_zeroDivisor == 1'b1) ? 1 : { {{{nrOfBits}}{1'b0}}, inputB };
          assign s_denS = (s_zeroDivisor == 1'b1) ? 1 : { {{{nrOfBits}}{inputB[{{nrOfBits}}-1]}}, inputB };
          assign s_quotS = $signed(s_numS) / $signed(s_denS);
          assign s_remS  = $signed(s_numS) % $signed(s_denS);
          assign s_quot  = ({{signed}} == 1) ? s_quotS : s_numU / s_denU;
          assign s_rem   = ({{signed}} == 1) ? s_remS : s_numU % s_denU;
          assign quotient  = s_quot[{{nrOfBits}}-1:0];
          assign remainder = s_rem[{{nrOfBits}}-1:0];
          """);
    }
    return contents.empty();
  }

  @Override
  public boolean isHdlSupportedTarget(AttributeSet attrs) {
    return true;
  }
}
