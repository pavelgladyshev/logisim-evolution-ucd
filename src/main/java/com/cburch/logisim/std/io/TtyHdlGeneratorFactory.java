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
 * On an FPGA the TTY becomes a serial transmitter (8N1) on its one mappable output, "TX", which is mapped to
 * the board's USB-UART, so that a terminal shows what the TTY component shows in a simulation:
 * <ul>
 *   <li>a character is written at the active clock edge when the write enable is not 0 (an unconnected write
 *       enable writes at every edge) and clear is not 1;</li>
 *   <li>printable characters are sent as they are, a new line ('\n' or '\r') as CR LF, a backspace as BS SP
 *       BS (which erases on a terminal), and a form feed or a rising clear input as ESC [2J ESC [H (clear
 *       screen); other control characters are ignored, like the TTY component does;</li>
 *   <li>characters are queued (1024, a block RAM) while the line sends them, so a circuit can print faster
 *       than the line for a while; when the queue is full, further characters are dropped.</li>
 * </ul>
 * Unlike the TTY component, the terminal wraps long lines at its own width.
 */
public class TtyHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  public static final long BAUD_RATE = 115200;
  static final int QUEUE_BITS = 10;
  private static final String CLKS_PER_BIT_STRING = "clksPerBit";
  private static final int CLKS_PER_BIT_ID = -1;
  private static final int QUEUE_ARRAY_ID = -2;

  public TtyHdlGeneratorFactory() {
    super();
    myParametersList.add(CLKS_PER_BIT_STRING, CLKS_PER_BIT_ID, HdlParameters.MAP_CONSTANT, 868);
    myWires
        .addWire("s_queueEmpty", 1)
        .addWire("s_queueFull", 1)
        .addWire("s_shown", 1)
        .addWire("s_clearEdge", 1)
        .addWire("s_queueWrite", 1)
        .addWire("s_queueData", 7)
        .addWire("s_isNewline", 1)
        .addWire("s_isBackspace", 1)
        .addWire("s_isClear", 1)
        .addWire("s_byte", 8)
        .addWire("s_last", 1)
        .addRegister("s_head", 7)
        .addRegister("s_headValid", 1)
        .addRegister("s_clearDelayed", 1)
        .addRegister("s_writePointer", QUEUE_BITS + 1)
        .addRegister("s_readPointer", QUEUE_BITS + 1)
        .addRegister("s_seq", 3)
        .addRegister("s_frame", 10)
        .addRegister("s_bitsLeft", 4)
        .addRegister("s_timer", 16)
        .addRegister("s_txN", 1);
    myTypedWires
        .addArray(QUEUE_ARRAY_ID, "ttyQueueArray", 7, 1 << QUEUE_BITS)
        .addWire("s_queue", QUEUE_ARRAY_ID);
    myPorts
        .add(Port.CLOCK, "clock", 1, Tty.CK)
        .add(Port.INPUT, "data", 7, Tty.IN, true)
        .add(Port.INPUT, "we", 1, Tty.WE, false)
        .add(Port.INPUT, "clear", 1, Tty.CLR, true)
        .add(Port.OUTPUT, "uartTx", 1, "open");
  }

  static int clocksPerBit() {
    return (int) Math.max(4, Math.round((double) Hdl.getFpgaClockFrequency() / BAUD_RATE));
  }

  @Override
  protected Map<String, String> getParameterMap(Netlist nets, netlistComponent componentInfo) {
    final var map = new TreeMap<>(super.getParameterMap(nets, componentInfo));
    map.put(CLKS_PER_BIT_STRING, Integer.toString(clocksPerBit()));
    return map;
  }

  @Override
  public Map<String, String> getPortMap(Netlist nets, Object mapInfo) {
    final var map = new TreeMap<String, String>(super.getPortMap(nets, mapInfo));
    if (mapInfo instanceof netlistComponent componentInfo) {
      map.put("uartTx", LineBuffer.formatHdl("{{1}}{{<}}{{2}}{{>}}", LOCAL_OUTPUT_BUBBLE_BUS_NAME,
          componentInfo.getLocalBubbleOutputStartId()));
    }
    return map;
  }

  @Override
  public LineBuffer getModuleFunctionality(Netlist theNetlist, AttributeSet attrs) {
    final var contents = LineBuffer.getHdlBuffer()
        .pair("clksPerBit", CLKS_PER_BIT_STRING)
        .pair("top", QUEUE_BITS)
        .pair("idx", QUEUE_BITS - 1);
    if (Hdl.isVhdl()) {
      contents.empty().addVhdlKeywords().addRemarkBlock("The character queue");
      contents.add("""
          s_queueEmpty <= '1' {{when}} s_writePointer = s_readPointer {{else}} '0';
          s_queueFull  <= '1' {{when}} s_writePointer({{top}}) /= s_readPointer({{top}}) {{and}}
                                    s_writePointer({{idx}} {{downto}} 0) = s_readPointer({{idx}} {{downto}} 0) {{else}} '0';
          -- the characters the TTY component shows: printable ones, new line, backspace and form feed
          s_shown      <= '1' {{when}} (unsigned(data) >= 32 {{and}} data /= "1111111") {{or}} data = "0001010" {{or}}
                                    data = "0001101" {{or}} data = "0001000" {{or}} data = "0001100" {{else}} '0';
          s_clearEdge  <= clear {{and}} {{not}}(s_clearDelayed);
          s_queueWrite <= {{not}}(s_queueFull) {{when}} s_clearEdge = '1' {{else}}
                          tick {{and}} we {{and}} {{not}}(clear) {{and}} s_shown {{and}} {{not}}(s_queueFull);
          s_queueData  <= "0001100" {{when}} s_clearEdge = '1' {{else}} data;
          uartTx       <= {{not}}(s_txN);

          -- a block RAM: written at the write enable, the oldest character read into s_head one clock later
          queueWrite : {{process}}(clock) {{is}}
          {{begin}}
             {{if}} (rising_edge(clock)) {{then}}
                {{if}} (s_writePointer(0) /= '0' {{and}} s_writePointer(0) /= '1') {{then}} -- simulation only
                   s_writePointer <= ({{others}} => '0');
                   s_clearDelayed <= '0';
                {{else}}
                   {{if}} (s_queueWrite = '1') {{then}}
                      s_queue(to_integer(unsigned(s_writePointer({{idx}} {{downto}} 0)))) <= s_queueData;
                      s_writePointer <= std_logic_vector(unsigned(s_writePointer) + 1);
                   {{end}} {{if}};
                   s_clearDelayed <= clear;
                {{end}} {{if}};
                s_head <= s_queue(to_integer(unsigned(s_readPointer({{idx}} {{downto}} 0))));
             {{end}} {{if}};
          {{end}} {{process}} queueWrite;
          """);
      contents.empty().addRemarkBlock("The transmitter (8N1); s_txN is the inverted line, so it idles high from power-up");
      contents.add("""
          s_isNewline   <= '1' {{when}} s_head = "0001010" {{or}} s_head = "0001101" {{else}} '0';
          s_isBackspace <= '1' {{when}} s_head = "0001000" {{else}} '0';
          s_isClear     <= '1' {{when}} s_head = "0001100" {{else}} '0';
          s_byte <= X"0D" {{when}} s_isNewline = '1' {{and}} s_seq = "000" {{else}}
                    X"0A" {{when}} s_isNewline = '1' {{else}}
                    X"20" {{when}} s_isBackspace = '1' {{and}} s_seq = "001" {{else}}
                    X"08" {{when}} s_isBackspace = '1' {{else}}
                    X"1B" {{when}} s_isClear = '1' {{and}} (s_seq = "000" {{or}} s_seq = "100") {{else}}
                    X"5B" {{when}} s_isClear = '1' {{and}} (s_seq = "001" {{or}} s_seq = "101") {{else}}
                    X"32" {{when}} s_isClear = '1' {{and}} s_seq = "010" {{else}}
                    X"4A" {{when}} s_isClear = '1' {{and}} s_seq = "011" {{else}}
                    X"48" {{when}} s_isClear = '1' {{else}}
                    '0' & s_head;
          s_last <= '1' {{when}} (s_isNewline = '1' {{and}} s_seq = "001") {{or}}
                                 (s_isBackspace = '1' {{and}} s_seq = "010") {{or}}
                                 (s_isClear = '1' {{and}} s_seq = "110") {{or}}
                                 (s_isNewline = '0' {{and}} s_isBackspace = '0' {{and}} s_isClear = '0') {{else}} '0';

          transmitter : {{process}}(clock) {{is}}
          {{begin}}
             {{if}} (rising_edge(clock)) {{then}}
                {{if}} (s_txN /= '0' {{and}} s_txN /= '1') {{then}} -- simulation only
                   s_txN         <= '0';
                   s_frame       <= ({{others}} => '1');
                   s_bitsLeft    <= ({{others}} => '0');
                   s_timer       <= ({{others}} => '0');
                   s_seq         <= ({{others}} => '0');
                   s_headValid   <= '0';
                   s_readPointer <= ({{others}} => '0');
                {{else}}
                   s_headValid <= {{not}}(s_queueEmpty); -- s_head holds the oldest character from the next clock
                   {{if}} (unsigned(s_timer) /= 0) {{then}}
                      s_timer <= std_logic_vector(unsigned(s_timer) - 1);
                   {{elsif}} (unsigned(s_bitsLeft) /= 0) {{then}}
                      s_txN      <= {{not}}(s_frame(0));
                      s_frame    <= '1' & s_frame(9 {{downto}} 1);
                      s_bitsLeft <= std_logic_vector(unsigned(s_bitsLeft) - 1);
                      s_timer    <= std_logic_vector(to_unsigned({{clksPerBit}} - 1, 16));
                   {{elsif}} (s_queueEmpty = '0' {{and}} s_headValid = '1') {{then}}
                      s_frame    <= '1' & s_byte & '0';
                      s_bitsLeft <= std_logic_vector(to_unsigned(10, 4));
                      {{if}} (s_last = '1') {{then}}
                         s_seq         <= ({{others}} => '0');
                         s_readPointer <= std_logic_vector(unsigned(s_readPointer) + 1);
                      {{else}}
                         s_seq <= std_logic_vector(unsigned(s_seq) + 1);
                      {{end}} {{if}};
                   {{end}} {{if}};
                {{end}} {{if}};
             {{end}} {{if}};
          {{end}} {{process}} transmitter;
          """);
    } else {
      contents.addRemarkBlock("The character queue");
      contents.add("""
          assign s_queueEmpty = (s_writePointer == s_readPointer) ? 1'b1 : 1'b0;
          assign s_queueFull  = ((s_writePointer[{{top}}] != s_readPointer[{{top}}]) &&
                                 (s_writePointer[{{idx}}:0] == s_readPointer[{{idx}}:0])) ? 1'b1 : 1'b0;
          // the characters the TTY component shows: printable ones, new line, backspace and form feed
          assign s_shown      = ((data >= 7'h20) && (data != 7'h7f)) || (data == 7'h0a) || (data == 7'h0d) ||
                                (data == 7'h08) || (data == 7'h0c);
          assign s_clearEdge  = clear & ~s_clearDelayed;
          assign s_queueWrite = s_clearEdge ? ~s_queueFull : tick & we & ~clear & s_shown & ~s_queueFull;
          assign s_queueData  = s_clearEdge ? 7'h0c : data;
          assign uartTx       = ~s_txN;

          initial
          begin
             s_writePointer = 0;
             s_readPointer  = 0;
             s_clearDelayed = 1'b0;
             s_frame        = 10'h3ff;
             s_bitsLeft     = 0;
             s_timer        = 0;
             s_seq          = 0;
             s_txN          = 1'b0;
             s_headValid    = 1'b0;
          end

          // a block RAM: written at the write enable, the oldest character read into s_head one clock later
          always @(posedge clock)
          begin
             if (s_queueWrite)
             begin
                s_queue[s_writePointer[{{idx}}:0]] <= s_queueData;
                s_writePointer <= s_writePointer + 1;
             end
             s_clearDelayed <= clear;
             s_head <= s_queue[s_readPointer[{{idx}}:0]];
          end
          """);
      contents.empty().addRemarkBlock("The transmitter (8N1); s_txN is the inverted line, so it idles high from power-up");
      contents.add("""
          assign s_isNewline   = (s_head == 7'h0a) || (s_head == 7'h0d);
          assign s_isBackspace = (s_head == 7'h08);
          assign s_isClear     = (s_head == 7'h0c);
          assign s_byte = s_isNewline   ? ((s_seq == 0) ? 8'h0d : 8'h0a) :
                          s_isBackspace ? ((s_seq == 1) ? 8'h20 : 8'h08) :
                          s_isClear     ? (((s_seq == 0) || (s_seq == 4)) ? 8'h1b :
                                           ((s_seq == 1) || (s_seq == 5)) ? 8'h5b :
                                           (s_seq == 2) ? 8'h32 : (s_seq == 3) ? 8'h4a : 8'h48) :
                          {1'b0, s_head};
          assign s_last = s_isNewline ? (s_seq == 1) : s_isBackspace ? (s_seq == 2) : s_isClear ? (s_seq == 6) : 1'b1;

          always @(posedge clock)
          begin
             s_headValid <= ~s_queueEmpty; // s_head holds the oldest character from the next clock
             if (s_timer != 0)
                s_timer <= s_timer - 1;
             else if (s_bitsLeft != 0)
             begin
                s_txN      <= ~s_frame[0];
                s_frame    <= {1'b1, s_frame[9:1]};
                s_bitsLeft <= s_bitsLeft - 1;
                s_timer    <= {{clksPerBit}} - 1;
             end
             else if (~s_queueEmpty & s_headValid)
             begin
                s_frame    <= {1'b1, s_byte, 1'b0};
                s_bitsLeft <= 4'd10;
                if (s_last)
                begin
                   s_seq         <= 0;
                   s_readPointer <= s_readPointer + 1;
                end
                else
                   s_seq <= s_seq + 1;
             end
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
