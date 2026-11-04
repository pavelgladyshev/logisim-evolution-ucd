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
import com.cburch.logisim.riscv.blockstorage.BlockStorage;
import com.cburch.logisim.std.io.Tty;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.std.memory.RamSdram;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Completes the board map of a circuit automatically and saves the result as a new file:
 *
 *   AutoMap <in.circ> <top circuit> <board> <out.circ>
 *
 * Existing (saved) maps are kept. TTY -> "UART_TX" and Keyboard -> "UART_RX"; a RAM whose FPGA memory is SDRAM
 * -> the board's SDRAM, pin for pin; a Block Storage's SD card -> the pins labelled TF_D0, TF_CLK, TF_CMD and TF_D3
 * (the Tang Nano 20K's TF slot); other 1-bit inputs go to the
 * buttons and other outputs to the LEDs, then everything goes to the PortIo pins; what is left becomes
 * constant 0 (inputs) or open (outputs). An FPGA pin is used once, even where the board offers it twice (an
 * LED that is also a header pin). Exit code 2 when the DRC fails.
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
  private final Set<String> taken = new HashSet<>();   // FPGA pins in use

  /** A free pin of a board resource of one of the given types (label filter: null = any but the UART). */
  private boolean mapTo(MapComponent comp, int pin, String label, IoComponentTypes... types) {
    for (final var type : types) {
      for (final var res : resources) {
        if (res.getType() != type) continue;
        final var name = res.getLabel() == null ? "" : res.getLabel();
        // the UART only for the TTY/Keyboard; not the Cmod's analog inputs (resistor dividers on the board)
        if (label == null ? (name.startsWith("UART_") || name.startsWith("AIN")) : !label.equals(name)) continue;
        for (var p = 0; p < res.getNrOfPins(); p++) {
          if (res.isPinMapped(p) || taken.contains(res.getPinLocation(p))) continue;
          if (comp.tryMap(pin, res, p)) {
            taken.add(res.getPinLocation(p));
            return true;
          }
        }
      }
    }
    return false;
  }

  /** The TF slot's pins for a Block Storage's SD_MISO, SD_SCK, SD_MOSI and SD_CS (its pins 0 to 3). */
  private static final String[] TF_PINS = {"TF_D0", "TF_CLK", "TF_CMD", "TF_D3"};

  /** Pin pin of a RAM in the SDRAM on the same pin of the board's SDRAM (whose pins are in the same order). */
  private boolean mapToSdram(MapComponent comp, int pin) {
    for (final var res : resources) {
      if (res.getType() != IoComponentTypes.Sdram || res.isPinMapped(pin)) continue;
      if (comp.tryMap(pin, res, pin)) {
        taken.add(res.getPinLocation(pin));
        return true;
      }
    }
    return false;
  }

  private void complete() {
    resources = myMappableResources.getIoComponentInformation().getComponents();
    final var keys = new ArrayList<>(myMappableResources.getMappableResources().keySet());
    keys.sort((a, b) -> String.join("/", a).compareTo(String.join("/", b)));
    for (final var comp : myMappableResources.getMappableResources().values()) {
      for (var pin = 0; pin < comp.getNrOfPins(); pin++) {
        if (comp.isBoardMapped(pin)) taken.add(comp.getPinLocation(pin));
      }
    }
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
        if (factory instanceof Ram && RamSdram.isSdram(comp.getAttributeSet())) {
          done = mapToSdram(comp, pin);
        } else if (factory instanceof BlockStorage && pin < TF_PINS.length) {
          done = mapTo(comp, pin, TF_PINS[pin], IoComponentTypes.PortIo);
        } else if (factory instanceof Tty && comp.isOutput(pin)) {
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
