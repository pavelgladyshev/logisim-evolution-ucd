package lstest;

import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.fpga.download.Download;
import com.cburch.logisim.fpga.file.BoardReaderClass;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Differential test of Logisim's HDL generators against its simulator: the same random inputs (and clock edges)
 * go through Logisim's simulation of a component and through the Verilog that Logisim generates for it.
 *
 *   HdlDiff build <template.circ> <specs.txt> <out.circ>   place each component, with a pin on every port
 *   HdlDiff hdl   <file.circ> <board>                     generate the HDL of "main" (HDL only)
 *   HdlDiff vectors <file.circ> <rows> <seed> <outdir> [dut.v]
 *       random inputs -> simulated outputs (expected.txt), inputs.hex and a testbench tb.v for the generated
 *       circuit "dut" (dut.v: its generated Verilog, needed when the components are clocked)
 *   HdlDiff tbvhdl <file.circ> <outdir> <dut_entity.vhd>
 *       a VHDL-2008 testbench tb.vhd for the same inputs.hex, for the generated VHDL of "dut"
 *
 * specs.txt: one component per line: name tool attr=value ... [skip=i,j] [clock=i|ram] [rare=i,j]
 *   skip: ports left unconnected; clock: the clock input, driven by a Clock (through tunnels "clk"); then each
 *   test step is: set the inputs, rising edge, falling edge, read the outputs; rare: inputs that are 1 only in
 *   1 of 16 steps (e.g. a clear).
 */
public class HdlDiff {

  static final String BOARD = "DIGILENT_CMOD_A7_35T";

  public static void main(String[] args) throws Exception {
    Main.headless = true;
    try {
      switch (args[0]) {
        case "build" -> build(new File(args[1]), new File(args[2]), new File(args[3]));
        case "hdl" -> hdl(new File(args[1]), args[2]);
        case "tbvhdl" -> tbVhdl(new File(args[1]), new File(args[2]), new File(args[3]));
      case "vectors" -> vectors(new File(args[1]), Integer.parseInt(args[2]), Long.parseLong(args[3]), new File(args[4]),
          args.length > 5 ? new File(args[5]) : null);
        default -> throw new IllegalArgumentException(args[0]);
      }
    } catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }
    System.exit(0);
  }

  static ComponentFactory findFactory(Project proj, String toolName) {
    for (Library lib : proj.getLogisimFile().getLibraries()) {
      final var tool = lib.getTool(toolName);
      if (tool instanceof AddTool add) return add.getFactory();
    }
    throw new IllegalArgumentException("no tool " + toolName);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static void setAttr(AttributeSet attrs, String name, String value) {
    for (Attribute attr : attrs.getAttributes()) {
      if (attr.getName().equals(name)) {
        attrs.setValue(attr, attr.parse(value));
        return;
      }
    }
    final var names = new ArrayList<String>();
    for (Attribute<?> attr : attrs.getAttributes()) names.add(attr.getName());
    throw new IllegalArgumentException("no attribute " + name + " in " + names);
  }

  static void add(Circuit circuit, Component comp) {
    final var mutation = new CircuitMutation(circuit);
    mutation.add(comp);
    mutation.execute();
  }

  static void build(File template, File specs, File out) throws Exception {
    final var loader = new Loader(null);
    final var file = loader.openLogisimFile(template, Collections.emptyMap());
    final var proj = new Project(file);
    final var dut = file.getCircuit("dut");
    final var main = file.getCircuit("main");
    final var pinFactory = findFactory(proj, "Pin");
    int index = 0;
    var clocked = false;
    for (final var line : Files.readAllLines(specs.toPath())) {
      final var t = line.trim();
      if (t.isEmpty() || t.startsWith("#")) continue;
      final var f = t.split("\\s+");
      final var name = f[0];
      final var factory = findFactory(proj, f[1].replace('_', ' '));
      final var attrs = factory.createAttributeSet();
      final var skip = new ArrayList<Integer>();
      final var rare = new ArrayList<Integer>();
      String clock = null;
      for (int i = 2; i < f.length; i++) {
        final var kv = f[i].split("=", 2);
        if (kv[0].equals("skip")) {
          for (final var s : kv[1].split(",")) skip.add(Integer.parseInt(s));
        } else if (kv[0].equals("rare")) {
          for (final var s : kv[1].split(",")) rare.add(Integer.parseInt(s));
        } else if (kv[0].equals("clock")) {
          clock = kv[1];
        } else {
          setAttr(attrs, kv[0], kv[1]);
        }
      }
      final var clockPort = clock == null ? -1
          : clock.equals("ram") ? com.cburch.logisim.std.memory.RamAppearance.getClkIndex(0, attrs)
          : Integer.parseInt(clock);
      final var loc = Location.create(200 + 400 * (index % 8), 200 + 300 * (index / 8), true);
      index++;
      final var comp = factory.createComponent(loc, attrs);
      add(dut, comp);
      final var ends = comp.getEnds();
      for (int p = 0; p < ends.size(); p++) {
        if (skip.contains(p)) continue;
        final EndData end = ends.get(p);
        if (p == clockPort) {
          add(dut, tunnel(proj, end.getLocation()));
          clocked = true;
          continue;
        }
        if (end.getType() == EndData.INPUT_OUTPUT) throw new IllegalArgumentException(name + ": port " + p + " is bidirectional");
        final var isInput = end.getType() == EndData.INPUT_ONLY;
        final var pinAttrs = pinFactory.createAttributeSet();
        pinAttrs.setValue(StdAttr.WIDTH, end.getWidth());
        pinAttrs.setValue(Pin.ATTR_TYPE, isInput ? Pin.INPUT : Pin.OUTPUT);
        pinAttrs.setValue(StdAttr.FACING, isInput ? Direction.EAST : Direction.WEST);
        pinAttrs.setValue(StdAttr.LABEL, name + "_" + (isInput ? (rare.contains(p) ? "r" : "i") : "o") + p);
        add(dut, pinFactory.createComponent(end.getLocation(), pinAttrs));
      }
    }
    if (clocked) {
      final var clockFactory = findFactory(proj, "Clock");
      final var where = Location.create(100, 200 + 300 * (index / 8 + 2), true);
      add(dut, clockFactory.createComponent(where, clockFactory.createAttributeSet()));
      add(dut, tunnel(proj, where));
    }
    final var sub = dut.getSubcircuitFactory();
    add(main, sub.createComponent(Location.create(100, 100, true), sub.createAttributeSet()));
    // Logisim wants a top level with mapped I/O: a dummy input and output (joined), mapped to a constant / open
    for (final var output : new boolean[] {false, true}) {
      final var pinAttrs = pinFactory.createAttributeSet();
      pinAttrs.setValue(Pin.ATTR_TYPE, output ? Pin.OUTPUT : Pin.INPUT);
      pinAttrs.setValue(StdAttr.FACING, output ? Direction.WEST : Direction.EAST);
      pinAttrs.setValue(StdAttr.LABEL, output ? "tout" : "tin");
      add(main, pinFactory.createComponent(Location.create(2000, 2000, true), pinAttrs));
    }
    try (final var os = new FileOutputStream(out)) {
      file.write(os, loader, out);
    }
    var xml = Files.readString(out.toPath());
    final var mainEnd = xml.indexOf("</circuit>", xml.indexOf("<circuit name=\"main\">"));
    final var map = "<boardmap boardname=\"" + BOARD + "\"><mc key=\"/tin\" vconst=\"0\" />"
        + "<mc key=\"/tout\" open=\"open\" /></boardmap>";
    xml = xml.substring(0, mainEnd) + map + xml.substring(mainEnd);
    Files.writeString(out.toPath(), xml);
    System.out.println("placed " + index + " components in " + out);
  }

  static Component tunnel(Project proj, Location where) {
    final var factory = findFactory(proj, "Tunnel");
    final var attrs = factory.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, "clk");
    return factory.createComponent(where, attrs);
  }

  static Project open(File circ) throws Exception {
    final var file = new Loader(null).openLogisimFile(circ, Collections.emptyMap());
    return new Project(file);
  }

  static void hdl(File circ, String board) throws Exception {
    final var proj = open(circ);
    final var info = new BoardReaderClass(AppPreferences.Boards.getBoardFilePath(board)).getBoardInformation();
    final var ok = new Download(proj, "main", 1000, info, null, false, false, true, 1.0, 1.0, null).runTty();
    System.out.println(ok ? "HDL generated" : "HDL generation FAILED");
    if (!ok) System.exit(1);
  }

  record PinInfo(Component comp, String label, int width, boolean input) {}

  static List<PinInfo> pins(Circuit circuit) {
    final var list = new ArrayList<PinInfo>();
    for (final var comp : circuit.getNonWires()) {
      if (!(comp.getFactory() instanceof Pin)) continue;
      final var attrs = comp.getAttributeSet();
      list.add(new PinInfo(comp, attrs.getValue(StdAttr.LABEL), attrs.getValue(StdAttr.WIDTH).getWidth(),
          attrs.getValue(Pin.ATTR_TYPE) == Pin.INPUT));
    }
    list.sort((a, b) -> a.label.compareTo(b.label));
    return list;
  }

  /**
   * A VHDL testbench equivalent to the Verilog one of vectors(): the same inputs.hex, the same clocking (Logisim's
   * own clock component), and the outputs in hex, one line per step, in actual_vhdl.txt.
   */
  static void tbVhdl(File circ, File outDir, File dutEntity) throws Exception {
    final var proj = open(circ);
    final var circuit = proj.getLogisimFile().getCircuit("dut");
    final var pins = pins(circuit);
    final var inputs = pins.stream().filter(PinInfo::input).toList();
    final var outputs = pins.stream().filter(p -> !p.input).toList();
    var clocked = false;
    for (final var comp : circuit.getNonWires()) clocked |= comp.getFactory() instanceof com.cburch.logisim.std.wiring.Clock;
    // port types from the generated entity: std_logic or std_logic_vector
    final var entity = Files.readString(dutEntity.toPath());
    final var scalar = new java.util.HashSet<String>();
    final var trees = new java.util.TreeSet<String>();
    final var m = java.util.regex.Pattern.compile("(?i)(\\w+)\\s*:\\s*(in|out)\\s+std_logic\\b(?!_vector)")
        .matcher(entity);
    while (m.find()) scalar.add(m.group(1));
    final var t = java.util.regex.Pattern.compile("\\b(logisimClockTree\\d+)\\b").matcher(entity);
    while (t.find()) trees.add(t.group(1));
    var total = 0;
    for (final var pin : inputs) total += ((pin.width + 3) / 4) * 4;
    try (final var tb = new PrintWriter(new File(outDir, "tb.vhd"))) {
      tb.println("LIBRARY ieee;");
      tb.println("USE ieee.std_logic_1164.all;");
      tb.println("USE ieee.numeric_std.all;");
      tb.println("USE std.textio.all;");
      tb.println();
      tb.println("ENTITY tb IS");
      tb.println("END tb;");
      tb.println();
      tb.println("ARCHITECTURE sim OF tb IS");
      tb.printf("   SIGNAL row : std_logic_vector(%d DOWNTO 0) := (OTHERS => '0');%n", total - 1);
      for (final var pin : pins) {
        tb.printf("   SIGNAL %s : %s;%n", pin.label,
            scalar.contains(pin.label) ? "std_logic" : String.format("std_logic_vector(%d DOWNTO 0)", pin.width - 1));
      }
      if (clocked) {
        tb.println("   SIGNAL gclk   : std_logic := '0';");
        tb.println("   SIGNAL tcount : unsigned(3 DOWNTO 0) := (OTHERS => '0');");
        tb.println("   SIGNAL tick   : std_logic := '0';");
        tb.println("   SIGNAL tree   : std_logic_vector(4 DOWNTO 0);");
        tb.println("   SIGNAL done   : boolean := false;");
      }
      tb.println("BEGIN");
      var pos = total;
      for (final var pin : inputs) {
        pos -= ((pin.width + 3) / 4) * 4;
        tb.printf("   %s <= row(%s);%n", pin.label, scalar.contains(pin.label)
            ? Integer.toString(pos) : String.format("%d DOWNTO %d", pos + pin.width - 1, pos));
      }
      final var conns = new ArrayList<String>();
      for (final var tree : trees) conns.add(tree + " => tree");
      for (final var pin : pins) conns.add(pin.label + " => " + pin.label);
      tb.println("   uut : ENTITY work.dut PORT MAP (" + String.join(", ", conns) + ");");
      if (clocked) {
        // Logisim's own clock component, with a tick every 16 FPGA clocks
        tb.println("   gclk <= NOT gclk AFTER 500 ps WHEN NOT done ELSE gclk;");
        tb.println("   ticks : PROCESS (gclk) BEGIN");
        tb.println("      IF rising_edge(gclk) THEN");
        tb.println("         tcount <= tcount + 1;");
        tb.println("         IF tcount = 15 THEN tick <= '1'; ELSE tick <= '0'; END IF;");
        tb.println("      END IF;");
        tb.println("   END PROCESS;");
        tb.println("   clk0 : ENTITY work.LogisimClockComponent");
        tb.println("      GENERIC MAP (highTicks => 1, lowTicks => 1, nrOfBits => 1, phase => 1)");
        tb.println("      PORT MAP (clockTick => tick, globalClock => gclk, clockBus => tree);");
      }
      tb.println("   stim : PROCESS");
      tb.println("      FILE fin  : text OPEN read_mode IS \"inputs.hex\";");
      tb.println("      FILE fout : text OPEN write_mode IS \"actual_vhdl.txt\";");
      tb.println("      VARIABLE lin, lout : line;");
      tb.printf("      VARIABLE v : std_logic_vector(%d DOWNTO 0);%n", total - 1);
      tb.println("   BEGIN");
      tb.println("      WHILE NOT endfile(fin) LOOP");
      tb.println("         readline(fin, lin);");
      tb.println("         hread(lin, v);");
      tb.println("         row <= v;");
      if (clocked) {
        tb.println("         WAIT UNTIL rising_edge(gclk) AND tree(2) = '1';   -- rising edge");
        tb.println("         WAIT UNTIL rising_edge(gclk) AND tree(3) = '1';   -- falling edge");
        tb.println("         WAIT UNTIL falling_edge(gclk);");
        tb.println("         WAIT UNTIL falling_edge(gclk);");
      } else {
        tb.println("         WAIT FOR 10 ns;");
      }
      for (var i = 0; i < outputs.size(); i++) {
        final var pin = outputs.get(i);
        final var value = scalar.contains(pin.label) ? "std_logic_vector'(0 => " + pin.label + ")" : pin.label;
        tb.printf("         write(lout, to_hstring(%s)%s);%n", value, i < outputs.size() - 1 ? " & ' '" : "");
      }
      tb.println("         writeline(fout, lout);");
      tb.println("      END LOOP;");
      if (clocked) tb.println("      done <= true;");
      tb.println("      WAIT;");
      tb.println("   END PROCESS;");
      tb.println("END sim;");
    }
  }

  static BigInteger randomValue(Random rnd, int width) {
    final var mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
    final var half = BigInteger.ONE.shiftLeft(width - 1);
    switch (rnd.nextInt(12)) {
      case 0: return BigInteger.ZERO;
      case 1: return BigInteger.ONE.and(mask);
      case 2: return mask;                                   // -1
      case 3: return half;                                   // most negative
      case 4: return half.subtract(BigInteger.ONE);          // most positive
      case 5: return mask.subtract(BigInteger.ONE).and(mask); // -2
      case 6: return BigInteger.valueOf(rnd.nextInt(8)).and(mask);
      case 7: return mask.subtract(BigInteger.valueOf(rnd.nextInt(8))).and(mask);
      default: return new BigInteger(width, rnd);
    }
  }

  static String hex(BigInteger v, int width) {
    final var digits = (width + 3) / 4;
    final var s = v.toString(16);
    return "0".repeat(Math.max(0, digits - s.length())) + s;
  }

  static String hex(Value v, int width) {
    if (!v.isFullyDefined()) return "?".repeat((width + 3) / 4);
    final var bits = v.getAll();
    var n = BigInteger.ZERO;
    for (int i = 0; i < width; i++) if (bits[i] == Value.TRUE) n = n.setBit(i);
    return hex(n, width);
  }

  static Value toValue(BigInteger v, int width) {
    final var bits = new Value[width];
    for (int i = 0; i < width; i++) bits[i] = v.testBit(i) ? Value.TRUE : Value.FALSE;
    return Value.create(bits);
  }

  static void vectors(File circ, int rows, long seed, File outDir, File dutHdl) throws Exception {
    final var proj = open(circ);
    final var circuit = proj.getLogisimFile().getCircuit("dut");
    final var pins = pins(circuit);
    final var inputs = pins.stream().filter(PinInfo::input).toList();
    final var outputs = pins.stream().filter(p -> !p.input).toList();
    final var state = CircuitState.createRootState(proj, circuit, Thread.currentThread());
    final var prop = state.getPropagator();
    var clocked = false;
    for (final var comp : circuit.getNonWires()) clocked |= comp.getFactory() instanceof com.cburch.logisim.std.wiring.Clock;

    prop.propagate();
    final var rnd = new Random(seed);
    outDir.mkdirs();
    try (final var in = new PrintWriter(new File(outDir, "inputs.hex"));
         final var exp = new PrintWriter(new File(outDir, "expected.txt"))) {
      for (int r = 0; r < rows; r++) {
        final var line = new StringBuilder();
        for (final var pin : inputs) {
          final var v = pin.label.matches(".*_r\\d+") ? BigInteger.valueOf(rnd.nextInt(16) == 0 ? 1 : 0)
              : randomValue(rnd, pin.width);
          final var ps = state.getInstanceState(pin.comp);
          Pin.FACTORY.driveInputPin(ps, toValue(v, pin.width));
          state.markComponentAsDirty(pin.comp);
          line.append(hex(v, pin.width));
        }
        in.println(line);
        prop.propagate();
        if (clocked) {                    // a rising and a falling clock edge
          for (int t = 0; t < 2; t++) {
            prop.toggleClocks();
            prop.propagate();
          }
        }
        final var outLine = new StringBuilder();
        for (final var pin : outputs) {
          if (outLine.length() > 0) outLine.append(' ');
          outLine.append(hex(Pin.FACTORY.getValue(state.getInstanceState(pin.comp)), pin.width));
        }
        exp.println(outLine);
      }
    }
    // testbench: one concatenated input word per row, outputs printed in hex in the same order
    var total = 0;
    for (final var pin : inputs) total += ((pin.width + 3) / 4) * 4;
    try (final var tb = new PrintWriter(new File(outDir, "tb.v"))) {
      tb.println("`timescale 1ns/1ps");
      tb.println("module tb;");
      tb.printf("   reg [%d:0] vec [0:%d];%n", total - 1, rows - 1);
      tb.printf("   reg [%d:0] row;%n", total - 1);
      for (final var pin : inputs) tb.printf("   wire [%d:0] %s;%n", pin.width - 1, pin.label);
      for (final var pin : outputs) tb.printf("   wire [%d:0] %s;%n", pin.width - 1, pin.label);
      var pos = total;
      for (final var pin : inputs) {
        final var digits = (pin.width + 3) / 4;
        pos -= digits * 4;
        tb.printf("   assign %s = row[%d:%d];%n", pin.label, pos + pin.width - 1, pos);
      }
      final var conns = new ArrayList<String>();
      if (clocked) {
        // Logisim's own clock component, with a tick every 16 FPGA clocks
        tb.println("   reg gclk = 0;");
        tb.println("   always #0.5 gclk = ~gclk;");
        tb.println("   reg [3:0] tcount = 0;");
        tb.println("   reg tick = 0;");
        tb.println("   always @(posedge gclk) begin tcount <= tcount + 1; tick <= (tcount == 15); end");
        tb.println("   wire [4:0] tree;");
        tb.println("   LogisimClockComponent #(.highTicks(1), .lowTicks(1), .nrOfBits(1), .phase(1))");
        tb.println("      clk0 (.clockBus(tree), .clockTick(tick), .globalClock(gclk));");
        final var hdl = Files.readString(dutHdl.toPath());
        final var m = java.util.regex.Pattern.compile("\\b(logisimClockTree\\d+)\\b").matcher(hdl);
        final var trees = new java.util.TreeSet<String>();
        while (m.find()) trees.add(m.group(1));
        for (final var t : trees) conns.add("." + t + "(tree)");
      }
      tb.print("   dut uut (");
      for (final var pin : pins) conns.add("." + pin.label + "(" + pin.label + ")");
      tb.print(String.join(", ", conns));
      tb.println(");");
      tb.println("   integer i, f;");
      tb.println("   initial begin");
      tb.println("      $readmemh(\"inputs.hex\", vec);");
      tb.println("      f = $fopen(\"actual.txt\", \"w\");");
      tb.printf("      for (i = 0; i < %d; i = i + 1) begin%n", rows);
      tb.println("         row = vec[i];");
      if (clocked) {
        tb.println("         @(posedge gclk); while (tree[2] !== 1'b1) @(posedge gclk);   // rising edge");
        tb.println("         @(posedge gclk); while (tree[3] !== 1'b1) @(posedge gclk);   // falling edge");
        tb.println("         @(negedge gclk); @(negedge gclk);");
      } else {
        tb.println("         #10;");
      }
      final var fmt = new ArrayList<String>();
      final var names = new ArrayList<String>();
      for (final var pin : outputs) {
        fmt.add("%h");
        names.add(pin.label);
      }
      tb.printf("         $fdisplay(f, \"%s\", %s);%n", String.join(" ", fmt), String.join(", ", names));
      tb.println("      end");
      tb.println("      $fclose(f);");
      tb.println("      $finish;");
      tb.println("   end");
      tb.println("endmodule");
    }
    try (final var cols = new PrintWriter(new File(outDir, "columns.txt"))) {
      for (final var pin : outputs) cols.println(pin.label + " " + pin.width);
    }
    System.out.println(inputs.size() + " inputs, " + outputs.size() + " outputs, " + rows + " rows");
  }
}
