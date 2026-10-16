/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.io;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.fpga.hdlgenerator.HdlParameters;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.util.LineBuffer;
import java.util.Map;
import java.util.TreeMap;

/**
 * On an FPGA the Keyboard becomes a serial receiver (8N1) on its one mappable input, "RX", which is mapped to
 * the board's USB-UART. Received characters are queued (up to the buffer length) like the keys typed into the
 * Keyboard component: "available" and "data" show the oldest one (0 and 0 when the queue is empty), read
 * enable at the active clock edge removes it (an unconnected read enable removes one at every edge), and clear
 * empties the queue. What a terminal sends for Enter (CR) becomes '\n' and for Backspace (DEL) '\b', as the
 * Keyboard component gets them from Logisim; other control characters than backspace, '\n' and form feed are
 * dropped, as in a simulation.
 */
public class KeyboardHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  static final int QUEUE_BITS = 8; // room for the longest buffer (256)
  private static final String CLKS_PER_BIT_STRING = "clksPerBit";
  private static final int CLKS_PER_BIT_ID = -1;
  private static final int QUEUE_ARRAY_ID = -2;
  private static final String BUFFER_LENGTH_STRING = "bufferLength";
  private static final int BUFFER_LENGTH_ID = -3;

  public KeyboardHdlGeneratorFactory() {
    super();
    myParametersList
        .add(CLKS_PER_BIT_STRING, CLKS_PER_BIT_ID, HdlParameters.MAP_CONSTANT, 868)
        .add(BUFFER_LENGTH_STRING, BUFFER_LENGTH_ID, HdlParameters.MAP_INT_ATTRIBUTE, Keyboard.ATTR_BUFFER);
    myWires
        .addWire("s_count", QUEUE_BITS + 1)
        .addWire("s_queueEmpty", 1)
        .addWire("s_queueFull", 1)
        .addWire("s_head", 7)
        .addWire("s_rxIn", 1)
        .addWire("s_rxChar", 7)
        .addWire("s_rxKeep", 1)
        .addRegister("s_writePointer", QUEUE_BITS + 1)
        .addRegister("s_readPointer", QUEUE_BITS + 1)
        .addRegister("s_rxSyncN", 2)
        .addRegister("s_rxBusy", 1)
        .addRegister("s_rxBit", 4)
        .addRegister("s_rxTimer", 16)
        .addRegister("s_rxByte", 8)
        .addRegister("s_rxDone", 1);
    myTypedWires
        .addArray(QUEUE_ARRAY_ID, "keyboardQueueArray", 7, 1 << QUEUE_BITS)
        .addWire("s_queue", QUEUE_ARRAY_ID);
    myPorts
        .add(Port.CLOCK, "clock", 1, Keyboard.CK)
        .add(Port.INPUT, "readEnable", 1, Keyboard.RE, false)
        .add(Port.INPUT, "clear", 1, Keyboard.CLR, true)
        .add(Port.INPUT, "uartRx", 1, "'1'")
        .add(Port.OUTPUT, "available", 1, Keyboard.AVL)
        .add(Port.OUTPUT, "data", 7, Keyboard.OUT);
  }

  @Override
  protected Map<String, String> getParameterMap(Netlist nets, netlistComponent componentInfo) {
    final var map = new TreeMap<>(super.getParameterMap(nets, componentInfo));
    map.put(CLKS_PER_BIT_STRING, Integer.toString(TtyHdlGeneratorFactory.clocksPerBit()));
    return map;
  }

  @Override
  public Map<String, String> getPortMap(Netlist nets, Object mapInfo) {
    final var map = new TreeMap<String, String>(super.getPortMap(nets, mapInfo));
    if (mapInfo instanceof netlistComponent componentInfo) {
      map.put("uartRx", LineBuffer.formatHdl("{{1}}{{<}}{{2}}{{>}}", LOCAL_INPUT_BUBBLE_BUS_NAME,
          componentInfo.getLocalBubbleInputStartId()));
    }
    return map;
  }

  @Override
  public LineBuffer getModuleFunctionality(Netlist theNetlist, AttributeSet attrs) {
    final var contents = LineBuffer.getHdlBuffer()
        .pair("clksPerBit", CLKS_PER_BIT_STRING)
        .pair("bufferLength", BUFFER_LENGTH_STRING)
        .pair("idx", QUEUE_BITS - 1);
    if (Hdl.isVhdl()) {
      contents.empty().addVhdlKeywords().addRemarkBlock("The receiver (8N1); s_rxSyncN holds the inverted line, so it idles high from power-up");
      contents.add("""
          s_rxIn <= {{not}}(s_rxSyncN(1));

          receiver : {{process}}(clock) {{is}}
          {{begin}}
             {{if}} (rising_edge(clock)) {{then}}
                {{if}} (s_rxBusy /= '0' {{and}} s_rxBusy /= '1') {{then}} -- simulation only
                   s_rxSyncN <= "00";
                   s_rxBusy  <= '0';
                   s_rxBit   <= ({{others}} => '0');
                   s_rxTimer <= ({{others}} => '0');
                   s_rxByte  <= ({{others}} => '0');
                   s_rxDone  <= '0';
                {{else}}
                   s_rxSyncN <= s_rxSyncN(0) & {{not}}(uartRx);
                   s_rxDone  <= '0';
                   {{if}} (s_rxBusy = '0') {{then}}
                      {{if}} (s_rxIn = '0') {{then}}
                         s_rxBusy  <= '1';
                         s_rxBit   <= ({{others}} => '0');
                         s_rxTimer <= std_logic_vector(to_unsigned({{clksPerBit}} / 2 - 1, 16));
                      {{end}} {{if}};
                   {{elsif}} (unsigned(s_rxTimer) /= 0) {{then}}
                      s_rxTimer <= std_logic_vector(unsigned(s_rxTimer) - 1);
                   {{else}}
                      s_rxTimer <= std_logic_vector(to_unsigned({{clksPerBit}} - 1, 16));
                      {{if}} (unsigned(s_rxBit) = 0) {{then}}
                         {{if}} (s_rxIn = '1') {{then}}
                            s_rxBusy <= '0';
                         {{else}}
                            s_rxBit <= "0001";
                         {{end}} {{if}};
                      {{elsif}} (unsigned(s_rxBit) <= 8) {{then}}
                         s_rxByte <= s_rxIn & s_rxByte(7 {{downto}} 1);
                         s_rxBit  <= std_logic_vector(unsigned(s_rxBit) + 1);
                      {{else}}
                         s_rxBusy <= '0';
                         s_rxDone <= s_rxIn;
                      {{end}} {{if}};
                   {{end}} {{if}};
                {{end}} {{if}};
             {{end}} {{if}};
          {{end}} {{process}} receiver;
          """);
      contents.empty().addRemarkBlock("The key queue");
      contents.add("""
          s_rxChar     <= "0001010" {{when}} s_rxByte(6 {{downto}} 0) = "0001101" {{else}}
                          "0001000" {{when}} s_rxByte(6 {{downto}} 0) = "1111111" {{else}} s_rxByte(6 {{downto}} 0);
          s_rxKeep     <= '1' {{when}} (unsigned(s_rxChar) >= 32 {{and}} s_rxChar /= "1111111") {{or}}
                                  s_rxChar = "0001000" {{or}} s_rxChar = "0001010" {{or}} s_rxChar = "0001100" {{else}} '0';
          s_count      <= std_logic_vector(unsigned(s_writePointer) - unsigned(s_readPointer));
          s_queueEmpty <= '1' {{when}} unsigned(s_count) = 0 {{else}} '0';
          s_queueFull  <= '1' {{when}} unsigned(s_count) >= {{bufferLength}} {{else}} '0';
          s_head       <= s_queue(to_integer(unsigned(s_readPointer({{idx}} {{downto}} 0))));
          available    <= {{not}}(s_queueEmpty);
          data         <= "0000000" {{when}} s_queueEmpty = '1' {{else}} s_head;

          queue : {{process}}(clock) {{is}}
          {{begin}}
             {{if}} (rising_edge(clock)) {{then}}
                {{if}} (clear = '1' {{or}} (s_writePointer(0) /= '0' {{and}} s_writePointer(0) /= '1')) {{then}}
                   s_writePointer <= ({{others}} => '0');
                   s_readPointer  <= ({{others}} => '0');
                {{else}}
                   {{if}} (s_rxDone = '1' {{and}} s_rxKeep = '1' {{and}} s_queueFull = '0') {{then}}
                      s_queue(to_integer(unsigned(s_writePointer({{idx}} {{downto}} 0)))) <= s_rxChar;
                      s_writePointer <= std_logic_vector(unsigned(s_writePointer) + 1);
                   {{end}} {{if}};
                   {{if}} (tick = '1' {{and}} readEnable = '1' {{and}} s_queueEmpty = '0') {{then}}
                      s_readPointer <= std_logic_vector(unsigned(s_readPointer) + 1);
                   {{end}} {{if}};
                {{end}} {{if}};
             {{end}} {{if}};
          {{end}} {{process}} queue;
          """);
    } else {
      contents.addRemarkBlock("The receiver (8N1); s_rxSyncN holds the inverted line, so it idles high from power-up");
      contents.add("""
          assign s_rxIn = ~s_rxSyncN[1];

          initial
          begin
             s_rxSyncN      = 2'b00;
             s_rxBusy       = 1'b0;
             s_rxBit        = 0;
             s_rxTimer      = 0;
             s_rxByte       = 0;
             s_rxDone       = 1'b0;
             s_writePointer = 0;
             s_readPointer  = 0;
          end

          always @(posedge clock)
          begin
             s_rxSyncN <= {s_rxSyncN[0], ~uartRx};
             s_rxDone  <= 1'b0;
             if (~s_rxBusy)
             begin
                if (~s_rxIn)
                begin
                   s_rxBusy  <= 1'b1;
                   s_rxBit   <= 0;
                   s_rxTimer <= {{clksPerBit}} / 2 - 1;
                end
             end
             else if (s_rxTimer != 0)
                s_rxTimer <= s_rxTimer - 1;
             else
             begin
                s_rxTimer <= {{clksPerBit}} - 1;
                if (s_rxBit == 0)
                begin
                   if (s_rxIn) s_rxBusy <= 1'b0;
                   else        s_rxBit  <= 1;
                end
                else if (s_rxBit <= 8)
                begin
                   s_rxByte <= {s_rxIn, s_rxByte[7:1]};
                   s_rxBit  <= s_rxBit + 1;
                end
                else
                begin
                   s_rxBusy <= 1'b0;
                   s_rxDone <= s_rxIn;
                end
             end
          end
          """);
      contents.empty().addRemarkBlock("The key queue");
      contents.add("""
          assign s_rxChar     = (s_rxByte[6:0] == 7'h0d) ? 7'h0a : (s_rxByte[6:0] == 7'h7f) ? 7'h08 : s_rxByte[6:0];
          assign s_rxKeep     = ((s_rxChar >= 7'h20) && (s_rxChar != 7'h7f)) || (s_rxChar == 7'h08) ||
                                (s_rxChar == 7'h0a) || (s_rxChar == 7'h0c);
          assign s_count      = s_writePointer - s_readPointer;
          assign s_queueEmpty = (s_count == 0) ? 1'b1 : 1'b0;
          assign s_queueFull  = (s_count >= {{bufferLength}}) ? 1'b1 : 1'b0;
          assign s_head       = s_queue[s_readPointer[{{idx}}:0]];
          assign available    = ~s_queueEmpty;
          assign data         = s_queueEmpty ? 7'd0 : s_head;

          always @(posedge clock)
             if (clear)
             begin
                s_writePointer <= 0;
                s_readPointer  <= 0;
             end
             else
             begin
                if (s_rxDone & s_rxKeep & ~s_queueFull)
                begin
                   s_queue[s_writePointer[{{idx}}:0]] <= s_rxChar;
                   s_writePointer <= s_writePointer + 1;
                end
                if (tick & readEnable & ~s_queueEmpty)
                   s_readPointer <= s_readPointer + 1;
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
