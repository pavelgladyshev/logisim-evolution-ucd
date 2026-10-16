/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.arith;

import com.cburch.logisim.data.AttributeOption;
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
 * HDL for the Multiplier, with the same results as its simulation (Multiplier.computeProduct):
 * product = a * b + carry in, where a is signed in the "two's complement" and "signed * unsigned" modes,
 * b only in "two's complement" mode, and the carry in is sign-extended unless both are unsigned.
 * Each operand is extended by one bit (zero or sign) and multiplied as signed, which gives the exact
 * product in 2 * width + 2 bits; the outputs are its low and high halves.
 */
public class MultiplierHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  private static final String NR_OF_BITS_STRING = "nrOfBits";
  private static final int NR_OF_BITS_ID = -1;
  private static final String CALC_BITS_STRING = "calcBits";
  private static final int CALC_BITS_ID = -2;
  private static final String SIGNED_A_STRING = "signedA";
  private static final int SIGNED_A_ID = -3;
  private static final String SIGNED_B_STRING = "signedB";
  private static final int SIGNED_B_ID = -4;
  private static final String EXT_BITS_STRING = "extBits";
  private static final int EXT_BITS_ID = -5;
  private static final String PROD_BITS_STRING = "prodBits";
  private static final int PROD_BITS_ID = -6;

  static final Map<AttributeOption, Integer> SIGNED_A_MAP = Map.of(
      Multiplier.UNSIGNED_OPTION, 0,
      Multiplier.SIGNED_UNSIGNED_OPTION, 1,
      Multiplier.SIGNED_OPTION, 1);
  static final Map<AttributeOption, Integer> SIGNED_B_MAP = Map.of(
      Multiplier.UNSIGNED_OPTION, 0,
      Multiplier.SIGNED_UNSIGNED_OPTION, 0,
      Multiplier.SIGNED_OPTION, 1);

  public MultiplierHdlGeneratorFactory() {
    super();
    myParametersList
        .add(NR_OF_BITS_STRING, NR_OF_BITS_ID)
        .add(CALC_BITS_STRING, CALC_BITS_ID, HdlParameters.MAP_MULTIPLY, 2)
        .add(EXT_BITS_STRING, EXT_BITS_ID, HdlParameters.MAP_OFFSET, 1)
        .add(PROD_BITS_STRING, PROD_BITS_ID, HdlParameters.MAP_OFFSET, 2)
        .add(SIGNED_A_STRING, SIGNED_A_ID, HdlParameters.MAP_ATTRIBUTE_OPTION, Multiplier.MODE_ATTR, SIGNED_A_MAP)
        .add(SIGNED_B_STRING, SIGNED_B_ID, HdlParameters.MAP_ATTRIBUTE_OPTION, Multiplier.MODE_ATTR, SIGNED_B_MAP);
    myWires
        .addWire("s_opA", EXT_BITS_ID)
        .addWire("s_opB", EXT_BITS_ID)
        .addWire("s_opC", EXT_BITS_ID)
        .addWire("s_product", PROD_BITS_ID);
    myPorts
        .add(Port.INPUT, "inputA", NR_OF_BITS_ID, Multiplier.IN0)
        .add(Port.INPUT, "inputB", NR_OF_BITS_ID, Multiplier.IN1)
        .add(Port.INPUT, "carryIn", NR_OF_BITS_ID, Multiplier.C_IN)
        .add(Port.OUTPUT, "multLow", NR_OF_BITS_ID, Multiplier.OUT)
        .add(Port.OUTPUT, "multHigh", NR_OF_BITS_ID, Multiplier.C_OUT);
  }

  @Override
  protected Map<String, String> getParameterMap(Netlist nets, netlistComponent componentInfo) {
    final var map = new TreeMap<>(super.getParameterMap(nets, componentInfo));
    final var width = componentInfo.getComponent().getAttributeSet().getValue(StdAttr.WIDTH).getWidth();
    map.put(PROD_BITS_STRING, Integer.toString(2 * width + 2));
    return map;
  }

  /**
   * In VHDL the data ports of a 1-bit multiplier are still vectors (0 downto 0), while its nets are
   * std_logic: such ports are connected element-wise (unconnected outputs stay "open").
   */
  @Override
  public SortedMap<String, String> getPortMap(Netlist nets, Object mapInfo) {
    final var map = new TreeMap<String, String>(super.getPortMap(nets, mapInfo));
    if (mapInfo instanceof netlistComponent comp && Hdl.isVhdl()
        && comp.getComponent().getAttributeSet().getValue(StdAttr.WIDTH).getWidth() == 1) {
      for (final var port : List.of("inputA", "inputB", "carryIn", "multLow", "multHigh")) {
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
  public LineBuffer getModuleFunctionality(Netlist TheNetlist, AttributeSet attrs) {
    final var contents = LineBuffer.getHdlBuffer()
        .pair("nrOfBits", NR_OF_BITS_STRING)
        .pair("calcBits", CALC_BITS_STRING)
        .pair("prodBits", PROD_BITS_STRING)
        .pair("signedA", SIGNED_A_STRING)
        .pair("signedB", SIGNED_B_STRING);
    if (Hdl.isVhdl()) {
      contents.empty().addVhdlKeywords().add("""
          s_opA     <= inputA({{nrOfBits}}-1) & inputA {{when}} {{signedA}} = 1 {{else}} '0' & inputA;
          s_opB     <= inputB({{nrOfBits}}-1) & inputB {{when}} {{signedB}} = 1 {{else}} '0' & inputB;
          s_opC     <= carryIn({{nrOfBits}}-1) & carryIn {{when}} {{signedA}} = 1 {{else}} '0' & carryIn;
          s_product <= std_logic_vector(signed(s_opA) * signed(s_opB) + resize(signed(s_opC), {{prodBits}}));
          multHigh  <= s_product({{calcBits}}-1 {{downto}} {{nrOfBits}});
          multLow   <= s_product({{nrOfBits}}-1 {{downto}} 0);
          """);
    } else {
      contents.add("""
          assign s_opA     = {(({{signedA}} == 1) ? inputA[{{nrOfBits}}-1] : 1'b0), inputA};
          assign s_opB     = {(({{signedB}} == 1) ? inputB[{{nrOfBits}}-1] : 1'b0), inputB};
          assign s_opC     = {(({{signedA}} == 1) ? carryIn[{{nrOfBits}}-1] : 1'b0), carryIn};
          assign s_product = $signed(s_opA) * $signed(s_opB) + $signed(s_opC);
          assign multHigh  = s_product[{{calcBits}}-1:{{nrOfBits}}];
          assign multLow   = s_product[{{nrOfBits}}-1:0];
          """);
    }
    return contents.empty();
  }
}
