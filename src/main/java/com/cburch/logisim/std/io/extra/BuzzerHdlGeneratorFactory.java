/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.io.extra;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.fpga.hdlgenerator.HdlParameters;
import com.cburch.logisim.fpga.hdlgenerator.SynthesizedClockHdlGeneratorFactory;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.util.LineBuffer;
import java.util.Map;
import java.util.TreeMap;

/**
 * On an FPGA the Buzzer becomes a square wave on its one mappable output, which is meant for a piezo
 * sounder on a header pin (JA1 on the Cmod A7). It is driven straight from the board clock rather than
 * from the tick, because a tone needs hundreds of hertz whatever rate the circuit is clocked at.
 *
 * <p>The frequency and the duty cycle follow the component: the FREQ input is read as Hz, or as tenths
 * of a hertz when the component's frequency-measure attribute is dHz, and the 8-bit duty cycle input is
 * a fraction of 256, defaulting to 128 (half) when it is left unconnected, as the component does.
 *
 * <p>Two things differ from a simulation, and neither can be helped on a single digital pin:
 * <ul>
 *   <li>the waveform is always a square wave. A pin carries a frequency and a duty cycle; it carries
 *       neither a waveshape nor an amplitude, so Sine, Triangle, Sawtooth and Noise all come out as the
 *       square wave of the same frequency. The pitch and the rhythm are right and the timbre is not;</li>
 *   <li>the volume input is ignored, for the same reason. A piezo on a pin is on or off.</li>
 * </ul>
 *
 * <p>The square wave itself is not an approximation of the component but the component's own definition
 * of one. Buzzer's Square strategy is {@code (hz * i) mod 1 < pw/256}; here a 32-bit phase accumulator
 * advances by {@code freq * phaseScale >> 8} every clock, so its top 8 bits are {@code (hz * i) mod 1}
 * scaled to 0..255, and comparing them against the duty cycle input is the same expression.
 */
public class BuzzerHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  private static final String PHASE_SCALE_STRING = "phaseScale";
  private static final int PHASE_SCALE_ID = -1;

  /**
   * The phase accumulator is 32 bits and the increment is {@code freq * phaseScale >> 8}. The shift is
   * what keeps the dHz case honest: without it the per-hertz step for tenths of a hertz would be 36
   * against a true 35.79, which is half a percent of pitch and audible. The increment is rounded rather
   * than truncated, which costs one adder and bounds the error at half a step.
   *
   * <p>Measured over all 16383 frequencies at the Cmod A7's 12 MHz: in Hz the worst error is 0.024%,
   * and in dHz 0.58%. Both worst cases sit at the very bottom of the range, where the output is a click
   * rate and not a pitch. From 20 Hz up, where there is a pitch to be wrong about, the worst is 0.005%
   * in Hz and 0.011% in dHz - a hundredth of the roughly 6% that separates two semitones.
   */
  private static final int PHASE_SHIFT = 8;

  public BuzzerHdlGeneratorFactory() {
    super();
    myParametersList.add(PHASE_SCALE_STRING, PHASE_SCALE_ID, HdlParameters.MAP_CONSTANT, 91626);
    myWires
        .addWire("s_tone", 1)
        .addRegister("s_phase", 32)
        .addRegister("s_increment", 32);
    myPorts
        // the board clock, not the tick: see the class comment
        .add(Port.INPUT, "fpgaClock", 1, SynthesizedClockHdlGeneratorFactory.SYNTHESIZED_CLOCK)
        .add(Port.INPUT, "freq", 14, Buzzer.FREQ, true)
        .add(Port.INPUT, "enable", 1, Buzzer.ENABLE, true)
        .add(Port.INPUT, "pw", 8, Buzzer.PW, true)
        .add(Port.OUTPUT, "buzz", 1, "open");
  }

  /**
   * How far the phase advances per clock for one unit of the frequency input, scaled by the shift
   * above. A unit is a hertz, or a tenth of one when the attribute says dHz, which is the whole cost of
   * that attribute: it is a different constant and no hardware at all.
   */
  static int phaseScale(AttributeSet attrs) {
    final var perUnit = Buzzer.isDeciHertz(attrs) ? 10.0 : 1.0;
    return (int) Math.round(Math.pow(2, 32 + PHASE_SHIFT) / (Hdl.getFpgaClockFrequency() * perUnit));
  }

  @Override
  protected Map<String, String> getParameterMap(Netlist nets, netlistComponent componentInfo) {
    final var map = new TreeMap<>(super.getParameterMap(nets, componentInfo));
    map.put(PHASE_SCALE_STRING, Integer.toString(phaseScale(componentInfo.getComponent().getAttributeSet())));
    return map;
  }

  @Override
  public Map<String, String> getPortMap(Netlist nets, Object mapInfo) {
    final var map = new TreeMap<String, String>(super.getPortMap(nets, mapInfo));
    if (mapInfo instanceof netlistComponent componentInfo) {
      map.put("buzz", LineBuffer.formatHdl("{{1}}{{<}}{{2}}{{>}}", LOCAL_OUTPUT_BUBBLE_BUS_NAME,
          componentInfo.getLocalBubbleOutputStartId()));
      // An unconnected duty cycle is half, as it is in the component. The framework can only tie a
      // floating input to all ones or all zeros, and this default is neither, so it is substituted
      // here - a zero would be a duty cycle of nothing, which is silence, and a legitimate value for
      // someone who drives the input deliberately.
      if (!componentInfo.isEndConnected(Buzzer.PW)) {
        map.put("pw", Hdl.getConstantVector(128, 8));
      }
    }
    return map;
  }

  @Override
  public LineBuffer getModuleFunctionality(Netlist theNetlist, AttributeSet attrs) {
    final var contents = LineBuffer.getHdlBuffer()
        .pair("phaseScale", PHASE_SCALE_STRING)
        .pair("shift", PHASE_SHIFT)
        .pair("round", 1 << (PHASE_SHIFT - 1));
    if (Hdl.isVhdl()) {
      contents.empty().addVhdlKeywords().addRemarkBlock(
          "A phase accumulator: its top 8 bits are the component's (hz * i) mod 1, scaled to 0..255");
      contents.add("""
          s_tone <= '1' {{when}} unsigned(s_phase(31 {{downto}} 24)) < unsigned(pw) {{else}} '0';
          buzz   <= enable {{and}} s_tone;

          accumulator : {{process}}(fpgaClock) {{is}}
          {{begin}}
             {{if}} (rising_edge(fpgaClock)) {{then}}
                {{if}} (s_phase(0) /= '0' {{and}} s_phase(0) /= '1') {{then}} -- simulation only
                   s_phase     <= ({{others}} => '0');
                   s_increment <= ({{others}} => '0');
                {{else}}
                   -- registered so the multiplier is not in the accumulator's path
                   s_increment <= std_logic_vector(resize(shift_right(
                                     unsigned(freq) * to_unsigned({{phaseScale}}, 24) + {{round}}, {{shift}}), 32));
                   s_phase     <= std_logic_vector(unsigned(s_phase) + unsigned(s_increment));
                {{end}} {{if}};
             {{end}} {{if}};
          {{end}} {{process}} accumulator;
          """);
    } else {
      contents.addRemarkBlock(
          "A phase accumulator: its top 8 bits are the component's (hz * i) mod 1, scaled to 0..255");
      contents.add("""
          assign s_tone = (s_phase[31:24] < pw) ? 1'b1 : 1'b0;
          assign buzz   = enable & s_tone;

          initial
          begin
             s_phase     = 0;
             s_increment = 0;
          end

          always @(posedge fpgaClock)
          begin
             // registered so the multiplier is not in the accumulator's path
             s_increment <= (freq * {{phaseScale}} + {{round}}) >> {{shift}};
             s_phase     <= s_phase + s_increment;
          end
          """);
    }
    return contents.empty();
  }

  @Override
  public boolean isHdlSupportedTarget(AttributeSet attrs) {
    return true;
  }
}
