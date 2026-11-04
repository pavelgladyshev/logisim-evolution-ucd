package lstest;

import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.proj.Project;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import javax.imageio.ImageIO;

/**
 * Draws a circuit into a PNG without Logisim's window, as File > Export Image does, for looking at a circuit a
 * script made. Components whose drawing needs a running simulation, such as the RV32IM CPU, show only in part.
 *
 * <pre>CircPng file.circ circuit out.png [scale]</pre>
 */
public class CircPng {
  public static void main(String[] args) throws Exception {
    Main.headless = true;
    final var file = new Loader(null).openLogisimFile(new File(args[0]), Collections.emptyMap());
    final var proj = new Project(file);
    final var circuit = file.getCircuit(args[1]);
    final double scale = args.length > 3 ? Double.parseDouble(args[3]) : 1.0;
    final var bds = circuit.getBounds().expand(20);
    final var img = new BufferedImage((int) (bds.getWidth() * scale), (int) (bds.getHeight() * scale),
        BufferedImage.TYPE_INT_RGB);
    final var g = (Graphics2D) img.getGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, img.getWidth(), img.getHeight());
    g.scale(scale, scale);
    g.translate(-bds.getX(), -bds.getY());
    final var state = CircuitState.createRootState(proj, circuit, Thread.currentThread());
    circuit.draw(new ComponentDrawContext(null, circuit, state, g, g, true), null);
    ImageIO.write(img, "PNG", new File(args[2]));
    System.exit(0);
  }
}
