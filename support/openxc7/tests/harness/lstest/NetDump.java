package lstest;

import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.instance.StdAttr;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Prints a circuit's components, each with its pins (position, width, direction), and then its nets: the pins
 * each set of connected wires joins, tunnels of the same label included. Tunnels are not listed as components;
 * in a net they appear as ~Tunnel#n[label].
 *
 * <pre>NetDump file.circ circuit</pre>
 */
public class NetDump {
  private static final Map<Location, Location> parent = new HashMap<>();

  private static Location find(Location l) {
    parent.putIfAbsent(l, l);
    var p = parent.get(l);
    if (!p.equals(l)) {
      p = find(p);
      parent.put(l, p);
    }
    return p;
  }

  private static void union(Location a, Location b) {
    final var ra = find(a);
    final var rb = find(b);
    if (!ra.equals(rb)) parent.put(ra, rb);
  }

  public static void main(String[] args) throws Exception {
    Main.headless = true;
    final var file = new Loader(null).openLogisimFile(new File(args[0]), Collections.emptyMap());
    final Circuit circ = file.getCircuit(args[1]);
    final List<Component> comps = new ArrayList<>(circ.getNonWires());
    comps.sort(Comparator.comparing((Component c) -> c.getFactory().getName())
        .thenComparing(c -> c.getLocation().getY()).thenComparing(c -> c.getLocation().getX()));
    final Map<Component, String> names = new HashMap<>();
    final Map<String, List<Location>> tunnels = new TreeMap<>();
    int n = 0;
    for (final var c : comps) {
      final var attrs = c.getAttributeSet();
      final var label = attrs.containsAttribute(StdAttr.LABEL) ? attrs.getValue(StdAttr.LABEL) : "";
      final var name = c.getFactory().getName().replace(' ', '_') + "#" + (n++)
          + (label == null || label.isEmpty() ? "" : "[" + label + "]");
      names.put(c, name);
      if (c.getFactory().getName().equals("Tunnel")) {
        tunnels.computeIfAbsent(label, k -> new ArrayList<>()).add(c.getEnds().get(0).getLocation());
        continue;
      }
      System.out.println("COMP " + name + " at " + c.getLocation());
      final var ends = c.getEnds();
      for (int i = 0; i < ends.size(); i++) {
        final var e = ends.get(i);
        final var dir = e.isInput() && e.isOutput() ? "inout" : e.isInput() ? "in" : "out";
        System.out.println("   p" + i + " " + e.getLocation() + " w" + e.getWidth().getWidth() + " " + dir);
      }
    }
    for (final Wire w : circ.getWires()) {
      union(w.getEnd0(), w.getEnd1());
      final var set = circ.getWireSet(w);  // Logisim's own idea of what this wire touches, tunnels included
      for (final Wire other : circ.getWires()) if (set.containsWire(other)) union(w.getEnd0(), other.getEnd0());
    }
    for (final var locs : tunnels.values()) for (final var l : locs) union(locs.get(0), l);
    final Map<Location, TreeSet<String>> nets = new HashMap<>();
    for (final var c : comps) {
      final var ends = c.getEnds();
      for (int i = 0; i < ends.size(); i++) {
        final var tag = c.getFactory().getName().equals("Tunnel") ? "~" + names.get(c) : names.get(c) + ".p" + i;
        nets.computeIfAbsent(find(ends.get(i).getLocation()), k -> new TreeSet<>()).add(tag);
      }
    }
    final List<TreeSet<String>> sorted = new ArrayList<>(nets.values());
    sorted.sort(Comparator.comparing(TreeSet::first));
    for (final var net : sorted) {
      if (net.size() > 1) System.out.println("NET " + String.join("  ", net));
    }
    System.exit(0);
  }
}
