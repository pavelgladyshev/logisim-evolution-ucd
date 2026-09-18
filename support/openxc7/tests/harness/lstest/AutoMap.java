package lstest;

import com.cburch.logisim.Main;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.fpga.data.FpgaIoInformationContainer;
import com.cburch.logisim.fpga.data.IoComponentTypes;
import com.cburch.logisim.fpga.data.MapComponent;
import com.cburch.logisim.fpga.download.DownloadBase;
import com.cburch.logisim.fpga.file.BoardReaderClass;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.io.Keyboard;
import com.cburch.logisim.std.io.Tty;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Completes the board map of a circuit automatically and saves the result as a new file:
 *
 *   AutoMap <in.circ> <top circuit> <board> <out.circ>
 *
 * Existing (saved) maps are kept. TTY -> "UART_TX" and Keyboard -> "UART_RX"; other 1-bit inputs go to the
 * buttons and other outputs to the LEDs, then everything goes to the PortIo pins; what is left becomes
 * constant 0 (inputs) or open (outputs). Exit code 2 when the DRC fails.
 */
public class AutoMap extends DownloadBase {

  public static void main(String[] args) throws Exception {
    Main.headless = true;
    final var loader = new Loader(null);
    final var inFile = new File(args[0]);
    final var file = loader.openLogisimFile(inFile, Collections.emptyMap());
    final var proj = new Project(file);
    final var top = file.getCircuit(args[1]);
    if (top == null) {
      System.out.println("AUTOMAP: no circuit " + args[1]);
      System.exit(3);
    }
    final var map = new AutoMap();
    map.myProject = proj;
    map.myBoardInformation =
        new BoardReaderClass(AppPreferences.Boards.getBoardFilePath(args[2])).getBoardInformation();
    top.annotate(proj, false, false);
    if (!map.performDrc(args[1], AppPreferences.HdlType.get())) {  // the HDL type of the preferences
      System.out.println("AUTOMAP: DRC failed");
      System.exit(2);
    }
    if (!map.mapDesign(args[1])) System.exit(3);
    map.complete();
    final var out = new File(args[3]);
    try (final var os = new FileOutputStream(out)) {
      file.write(os, loader, out);
    }
    System.exit(0);
  }

  private List<FpgaIoInformationContainer> resources;

  /** A free pin of a board resource of one of the given types (label filter: null = any but the UART). */
  private boolean mapTo(MapComponent comp, int pin, String label, IoComponentTypes... types) {
    for (final var type : types) {
      for (final var res : resources) {
        if (res.getType() != type) continue;
        final var name = res.getLabel() == null ? "" : res.getLabel();
        // the UART only for the TTY/Keyboard; not the Cmod's analog inputs (resistor dividers on the board)
        if (label == null ? (name.startsWith("UART_") || name.startsWith("AIN")) : !label.equals(name)) continue;
        for (var p = 0; p < res.getNrOfPins(); p++) {
          if (res.isPinMapped(p)) continue;
          if (comp.tryMap(pin, res, p)) return true;
        }
      }
    }
    return false;
  }

  private void complete() {
    resources = myMappableResources.getIoComponentInformation().getComponents();
    final var keys = new ArrayList<>(myMappableResources.getMappableResources().keySet());
    keys.sort((a, b) -> String.join("/", a).compareTo(String.join("/", b)));
    var toBoard = 0;
    var toConstant = 0;
    for (final var key : keys) {
      final var comp = myMappableResources.getMappableResources().get(key);
      final var factory = comp.getComponentFactory();
      final var summary = new StringBuilder();
      for (var pin = 0; pin < comp.getNrOfPins(); pin++) {
        if (comp.isMapped(pin)) {
          summary.append(" kept");
          continue;
        }
        var done = false;
        if (factory instanceof Tty && comp.isOutput(pin)) {
          done = mapTo(comp, pin, "UART_TX", IoComponentTypes.Led);
        } else if (factory instanceof Keyboard && comp.isInput(pin)) {
          done = mapTo(comp, pin, "UART_RX", IoComponentTypes.Button);
        } else if (comp.isInput(pin)) {
          done = mapTo(comp, pin, null, IoComponentTypes.Button, IoComponentTypes.PortIo);
        } else if (comp.isOutput(pin)) {
          done = mapTo(comp, pin, null, IoComponentTypes.Led, IoComponentTypes.RgbLed, IoComponentTypes.PortIo);
        } else {
          done = mapTo(comp, pin, null, IoComponentTypes.PortIo);
        }
        if (done) {
          toBoard++;
          summary.append(" ").append(comp.getDisplayString(pin));
        } else {
          toConstant++;
          if (comp.isInput(pin)) comp.tryConstantMap(pin, 0);
          else comp.tryOpenMap(pin);
          summary.append(comp.isInput(pin) ? " const0" : " open");
        }
      }
      System.out.println("AUTOMAP: " + String.join("/", key.subList(1, key.size())) + " ->" + summary);
    }
    System.out.println("AUTOMAP: " + toBoard + " pins on the board, " + toConstant + " constant/open, complete="
        + myMappableResources.isCompletelyMapped());
  }
}
