/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.gui;

import static com.cburch.logisim.fpga.Strings.S;

import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.menu.EditHandler;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.MenuListener;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.io.TtyHdlGeneratorFactory;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.WindowMenuItemManager;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * FPGA &gt; Serial Terminal: a terminal on a serial port, such as a board's USB serial port, to
 * which a circuit on the FPGA has its TTY and Keyboard components mapped (115200 baud, 8N1: see
 * TtyHdlGeneratorFactory and KeyboardHdlGeneratorFactory).
 *
 * <p>What the board sends is shown as a terminal shows it ({@link TerminalScreen}). What is typed is
 * sent as a terminal sends it: printable characters as they are (ISO-8859-1), Enter as CR,
 * Backspace as DEL, Tab and Escape; a paste is typed, a character a millisecond and a line every
 * 20, so that a circuit reading the Keyboard can keep up. Copy and paste are the usual keys, the
 * Edit menu and the text's pop-up menu.
 *
 * <p>The list of ports puts first the one most likely the board's ({@link #boardScore}), and it
 * follows the ports that come and go while not connected. A port and speed are remembered, to be
 * offered next time, once the board has sent something through them. There is one window for
 * all projects; closing it disconnects.
 */
public final class SerialTerminal extends LFrame.SubWindow implements LocaleListener {
  private static final long serialVersionUID = 1L;

  /** The speeds to choose from; the TTY and Keyboard on an FPGA use 115200. */
  static final int[] SPEEDS = {9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600};

  /** A paste is typed, one character this often (ms), waiting this long after a new line. */
  static final long PASTE_CHARACTER_MILLIS = 1;

  static final long PASTE_LINE_MILLIS = 20;

  /** While not connected, the list of ports is read this often (ms). */
  private static final int PORT_CHECK_MILLIS = 2000;

  private static SerialTerminal window;

  private final TerminalScreen screen = new TerminalScreen();
  private final JTextArea textArea = new JTextArea(24, 80);
  private final JLabel portLabel = new JLabel();
  private final JComboBox<PortChoice> portBox = new JComboBox<>();
  private final JLabel speedLabel = new JLabel();
  private final JComboBox<Integer> speedBox = new JComboBox<>();
  private final JButton connectButton = new JButton();
  private final JButton clearButton = new JButton();
  private final JLabel status = new JLabel();
  private final JMenuItem copyItem = new JMenuItem();
  private final JMenuItem pasteItem = new JMenuItem();
  private final JMenuItem selectAllItem = new JMenuItem();
  private final JMenuItem clearItem = new JMenuItem();
  private final Timer portCheck = new Timer(PORT_CHECK_MILLIS, e -> checkPorts());
  private final ConcurrentLinkedQueue<byte[]> received = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean showScheduled = new AtomicBoolean();
  private final WindowManager windowManager = new WindowManager();
  private Connection connection;
  private boolean showing;
  private String statusKey = "serialTerminalNotConnected";
  private Object[] statusArguments = {};

  /** A port in the list: its path (to open it), its name, what the system calls it. */
  record PortChoice(String path, String name, String description, int score) {
    @Override
    public String toString() {
      final var lowerName = name.toLowerCase(Locale.ROOT);
      return lowerName.contains(description.toLowerCase(Locale.ROOT))
          ? name
          : name + " - " + description;
    }
  }

  /** Shows the terminal, made at its first use, in front, near this component. */
  public static void showWindow(Component parent) {
    if (window == null) {
      window = new SerialTerminal();
      window.refreshPorts();
      window.pack();
      window.setLocationRelativeTo(parent);
    } else {
      window.refreshPorts();
    }
    window.setVisible(true);
    window.toFront();
    window.windowManager.frameOpened(window);
    window.portCheck.start();
    window.textArea.requestFocusInWindow();
  }

  private SerialTerminal() {
    super(null);
    setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            portCheck.stop();
            disconnect();
            setStatus("serialTerminalNotConnected");
          }
        });

    // The text can be edited, so that the keys for editing work (in a read-only text they beep)
    // and the cursor shows, but only the terminal changes it: what is typed goes to the port.
    ((AbstractDocument) textArea.getDocument()).setDocumentFilter(new ScreenFilter());
    textArea.setLineWrap(true);
    textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, AppPreferences.getScaled(13)));
    textArea.setFocusTraversalKeysEnabled(false);
    textArea.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyTyped(KeyEvent e) {
            if (e.isMetaDown()) {
              return; // a menu shortcut on a Mac
            }
            send(bytesForKey(e.getKeyChar()), false);
            e.consume();
          }
        });
    final var menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuKey), "serialTerminalPaste");
    textArea
        .getInputMap()
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.SHIFT_DOWN_MASK), "serialTerminalPaste");
    textArea.getActionMap().put("serialTerminalPaste", action(this::paste));

    copyItem.addActionListener(e -> textArea.copy());
    pasteItem.addActionListener(e -> paste());
    selectAllItem.addActionListener(e -> textArea.selectAll());
    clearItem.addActionListener(e -> clearScreen());
    final var popup = new JPopupMenu();
    popup.add(copyItem);
    popup.add(pasteItem);
    popup.add(selectAllItem);
    popup.addSeparator();
    popup.add(clearItem);
    textArea.setComponentPopupMenu(popup);

    // as wide as a Mac's name for the Tang Nano's UART and a description, whatever is listed
    portBox.setPrototypeDisplayValue(
        new PortChoice("", "cu.usbserial-20250303171", "USB Serial Converter B", 0));
    portBox.addActionListener(e -> updateControls());
    for (final var speed : SPEEDS) {
      speedBox.addItem(speed);
    }
    speedBox.setSelectedItem(AppPreferences.FPGA_SERIAL_BAUD.get());
    if (speedBox.getSelectedIndex() < 0) {
      speedBox.setSelectedItem((int) TtyHdlGeneratorFactory.BAUD_RATE);
    }
    connectButton.addActionListener(
        e -> {
          if (connection == null) {
            connect();
          } else {
            disconnect();
            setStatus("serialTerminalNotConnected");
            refreshPorts();
          }
        });
    clearButton.addActionListener(e -> clearScreen());

    final var controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(portLabel);
    controls.add(portBox);
    controls.add(speedLabel);
    controls.add(speedBox);
    controls.add(connectButton);
    controls.add(clearButton);
    status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    final var content = new JPanel(new BorderLayout());
    content.add(controls, BorderLayout.NORTH);
    content.add(new JScrollPane(textArea), BorderLayout.CENTER);
    content.add(status, BorderLayout.SOUTH);
    setContentPane(content);

    new TerminalMenuListener(getLogisimMenuBar(), new TerminalEditHandler());
    LocaleManager.addLocaleListener(this);
    localeChanged();
  }

  /**
   * How likely this port is a board's: 3 for a USB port whose name ends in 1, 2 for another USB
   * port, 0 for other ports (Bluetooth, debug consoles, a PC's own COM1). The Tang Nano's and the
   * Cmod A7's USB chips have two ports, JTAG first and the UART second (cu.usbserial-...1 on a Mac,
   * ttyUSB1 on Linux); on Windows only the UART is left a COM port once the JTAG port has the
   * WinUSB driver.
   */
  static int boardScore(String name, int vendorId) {
    final var lowerName = name.toLowerCase(Locale.ROOT);
    final var usb = vendorId != -1 || lowerName.contains("usb") || lowerName.contains("acm");
    if (!usb) {
      return 0;
    }
    return name.endsWith("1") ? 3 : 2;
  }

  /** What the system says about a port (the vendor id is -1 for a port not on USB). */
  record PortInfo(String path, String name, String description, int vendorId, String serialNumber) {}

  /** The system's ports to offer ({@link #choices}). */
  static List<PortChoice> listPorts() {
    final var ports = new ArrayList<PortInfo>();
    for (final var port : SerialPort.getCommPorts()) {
      ports.add(
          new PortInfo(
              port.getSystemPortPath(),
              port.getSystemPortName(),
              port.getPortDescription(),
              port.getVendorID(),
              port.getSerialNumber()));
    }
    return choices(ports);
  }

  /**
   * The ports to offer, the likeliest board's first ({@link #boardScore}). Left out are a Mac's
   * tty.* ports (dial-in twins of its cu.* ports) and the first of two ports of one USB chip
   * (both with its serial number): a board's chip (the Tang Nano's, the Cmod A7's: an FT2232 or a
   * copy of one) has JTAG there and the UART on the second. Nothing talks on the JTAG port, and
   * while it is open the FPGA cannot be loaded ("unable to claim usb device").
   */
  static List<PortChoice> choices(List<PortInfo> ports) {
    final var names = new HashSet<String>();
    for (final var port : ports) {
      names.add(port.name());
    }
    final var listed = new ArrayList<PortInfo>();
    final var bySerialNumber = new HashMap<String, List<PortInfo>>();
    for (final var port : ports) {
      if (port.name().startsWith("tty.") && names.contains("cu." + port.name().substring(4))) {
        continue;
      }
      listed.add(port);
      final var serialNumber = port.serialNumber() == null ? "" : port.serialNumber().trim();
      if (port.vendorId() != -1 && !serialNumber.isEmpty() && !serialNumber.equals("Unknown")) {
        bySerialNumber.computeIfAbsent(serialNumber, key -> new ArrayList<>()).add(port);
      }
    }
    for (final var chip : bySerialNumber.values()) {
      if (chip.size() == 2) {
        // the first by number: cu.usbserial-...0 before ...1, ttyUSB9 before ttyUSB10
        listed.remove(
            Collections.min(
                chip,
                Comparator.comparingInt((PortInfo port) -> port.name().length())
                    .thenComparing(PortInfo::name)));
      }
    }
    final var choices = new ArrayList<PortChoice>();
    for (final var port : listed) {
      choices.add(
          new PortChoice(
              port.path(),
              port.name(),
              port.description() == null ? "" : port.description().trim(),
              boardScore(port.name(), port.vendorId())));
    }
    choices.sort(
        Comparator.comparingInt(PortChoice::score).reversed().thenComparing(PortChoice::name));
    return choices;
  }

  /** What a terminal sends for this typed character (nothing for a character it cannot send). */
  static byte[] bytesForKey(char c) {
    return switch (c) {
      case '\n', '\r' -> new byte[] {'\r'}; // Enter: the Keyboard on an FPGA makes it '\n'
      case '\b', 0x7F -> new byte[] {0x7F}; // Backspace: DEL, which the Keyboard makes '\b'
      case '\t', 0x1B -> new byte[] {(byte) c};
      default -> isSendable(c) ? new byte[] {(byte) c} : new byte[0];
    };
  }

  /** What is typed for pasted text: its printable characters and tabs, and CR for each new line. */
  static byte[] bytesForPaste(String text) {
    final var bytes = new ByteArrayOutputStream();
    for (var i = 0; i < text.length(); i++) {
      final var c = text.charAt(i);
      if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
        continue; // CR LF is one new line
      }
      if (c == '\n' || c == '\r') {
        bytes.write('\r');
      } else if (c == '\t' || isSendable(c)) {
        bytes.write(c);
      }
    }
    return bytes.toByteArray();
  }

  private static boolean isSendable(char c) {
    return c >= 0x20 && c <= 0xFF && !(c >= 0x7F && c < 0xA0);
  }

  private static AbstractAction action(Runnable runnable) {
    return new AbstractAction() {
      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(ActionEvent e) {
        runnable.run();
      }
    };
  }

  private void checkPorts() {
    if (connection == null && isVisible() && !portBox.isPopupVisible()) {
      refreshPorts();
    }
  }

  /** Reads the list of ports again, keeping the port chosen if it is still there. */
  private void refreshPorts() {
    List<PortChoice> ports;
    try {
      ports = listPorts();
    } catch (RuntimeException | LinkageError e) {
      ports = List.of(); // jSerialComm could not load its native library
      setStatus("serialTerminalNoLibrary", String.valueOf(e.getMessage()));
    }
    final var shown = new ArrayList<PortChoice>();
    for (var i = 0; i < portBox.getItemCount(); i++) {
      shown.add(portBox.getItemAt(i));
    }
    if (!ports.equals(shown)) {
      final var selected = (PortChoice) portBox.getSelectedItem();
      final var wanted = selected != null ? selected.path() : AppPreferences.FPGA_SERIAL_PORT.get();
      portBox.removeAllItems();
      PortChoice choice = null;
      for (final var port : ports) {
        portBox.addItem(port);
        if (port.path().equals(wanted)) {
          choice = port;
        }
      }
      if (choice == null && !ports.isEmpty() && ports.get(0).score() > 0) {
        choice = ports.get(0);
      }
      portBox.setSelectedIndex(choice == null ? -1 : ports.indexOf(choice));
    }
    if (connection == null
        && List.of("serialTerminalNotConnected", "serialTerminalNoBoard", "serialTerminalTypeNotConnected")
            .contains(statusKey)) {
      final var board = ports.stream().anyMatch(port -> port.score() > 0);
      setStatus(board ? "serialTerminalNotConnected" : "serialTerminalNoBoard");
    }
    updateControls();
  }

  private void connect() {
    final var choice = (PortChoice) portBox.getSelectedItem();
    final var speed = (Integer) speedBox.getSelectedItem();
    if (choice == null || speed == null) {
      return;
    }
    try {
      connection = new Connection(choice.path(), choice.name(), speed);
    } catch (IOException e) {
      setStatus("serialTerminalOpenFailed", choice.name(), e.getMessage());
      return;
    }
    connection.start(this::receive, this::lose);
    setStatus("serialTerminalConnected", choice.name(), speed);
    updateControls();
    textArea.requestFocusInWindow();
  }

  private void disconnect() {
    if (connection != null) {
      connection.close();
      connection = null;
      updateControls();
    }
  }

  /** From the reader thread: shows these bytes soon. */
  private void receive(byte[] bytes) {
    received.add(bytes);
    if (showScheduled.compareAndSet(false, true)) {
      SwingUtilities.invokeLater(this::showReceived);
    }
  }

  /** From the reader or writer thread: the port has gone (unplugged?) or failed. */
  private void lose(Connection lost) {
    SwingUtilities.invokeLater(
        () -> {
          if (connection == lost) {
            disconnect();
            setStatus("serialTerminalLost", lost.name);
            refreshPorts();
          }
        });
  }

  private void showReceived() {
    showScheduled.set(false);
    var any = false;
    for (var bytes = received.poll(); bytes != null; bytes = received.poll()) {
      screen.write(bytes, 0, bytes.length);
      any = true;
    }
    if (any && connection != null && !connection.remembered) {
      // the board talks on this port: the one to offer next time (a silent port, such as a
      // board's JTAG port tried by mistake, is not remembered)
      connection.remembered = true;
      AppPreferences.FPGA_SERIAL_PORT.set(connection.path);
      AppPreferences.FPGA_SERIAL_BAUD.set(connection.speed);
    }
    showScreen();
  }

  /** Copies what changed on the screen into the text area. */
  private void showScreen() {
    final var from = screen.takeChangedFrom();
    if (from < 0) {
      return;
    }
    final var document = textArea.getDocument();
    showing = true;
    try {
      if (from <= document.getLength()) {
        document.remove(from, document.getLength() - from);
        document.insertString(from, screen.getText(from), null);
      } else {
        textArea.setText(screen.getText());
      }
    } catch (BadLocationException e) {
      textArea.setText(screen.getText());
    } finally {
      showing = false;
    }
    if (textArea.getSelectionStart() == textArea.getSelectionEnd()) {
      textArea.setCaretPosition(screen.getCursor()); // which also shows the end of the text
    }
  }

  private void clearScreen() {
    screen.clear();
    showScreen();
    textArea.requestFocusInWindow();
  }

  private void paste() {
    try {
      final var text = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
      if (text instanceof String string) {
        send(bytesForPaste(string), true);
      }
    } catch (UnsupportedFlavorException | IOException | IllegalStateException e) {
      // nothing to paste
    }
  }

  private void send(byte[] bytes, boolean paced) {
    if (bytes.length == 0) {
      return;
    }
    if (connection == null) {
      setStatus("serialTerminalTypeNotConnected");
    } else {
      connection.send(bytes, paced);
    }
  }

  private void setStatus(String key, Object... arguments) {
    statusKey = key;
    statusArguments = arguments;
    status.setText(S.get(key, arguments));
  }

  private void updateControls() {
    final var connected = connection != null;
    final var choice = portBox.getSelectedItem();
    portBox.setEnabled(!connected);
    portBox.setToolTipText(choice == null ? null : choice.toString());
    speedBox.setEnabled(!connected);
    connectButton.setEnabled(connected || choice != null);
    connectButton.setText(S.get(connected ? "serialTerminalDisconnect" : "serialTerminalConnect"));
    setTitle(
        connected
            ? S.get("serialTerminalTitle") + " - " + connection.name
            : S.get("serialTerminalTitle"));
  }

  @Override
  public void localeChanged() {
    // the Connect button keeps its width when it says Disconnect
    connectButton.setPreferredSize(null);
    var width = 0;
    for (final var key : new String[] {"serialTerminalConnect", "serialTerminalDisconnect"}) {
      connectButton.setText(S.get(key));
      width = Math.max(width, connectButton.getPreferredSize().width);
    }
    connectButton.setPreferredSize(new Dimension(width, connectButton.getPreferredSize().height));
    portLabel.setText(S.get("serialTerminalPort"));
    speedLabel.setText(S.get("serialTerminalSpeed"));
    clearButton.setText(S.get("serialTerminalClear"));
    copyItem.setText(S.get("serialTerminalCopy"));
    pasteItem.setText(S.get("serialTerminalPaste"));
    selectAllItem.setText(S.get("serialTerminalSelectAll"));
    clearItem.setText(S.get("serialTerminalClear"));
    status.setText(S.get(statusKey, statusArguments));
    windowManager.setText(S.get("serialTerminalTitle"));
    updateControls();
  }

  /** Lets only the terminal change the text. */
  private final class ScreenFilter extends DocumentFilter {
    @Override
    public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes)
        throws BadLocationException {
      if (showing) {
        super.insertString(bypass, offset, text, attributes);
      }
    }

    @Override
    public void remove(FilterBypass bypass, int offset, int length) throws BadLocationException {
      if (showing) {
        super.remove(bypass, offset, length);
      }
    }

    @Override
    public void replace(
        FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
        throws BadLocationException {
      if (showing) {
        super.replace(bypass, offset, length, text, attributes);
      }
    }
  }

  /** The terminal in the Window menu while it is open. */
  private final class WindowManager extends WindowMenuItemManager {
    WindowManager() {
      super(S.get("serialTerminalTitle"), false);
    }

    @Override
    public JFrame getJFrame(boolean create, Component parent) {
      return SerialTerminal.this;
    }
  }

  /** Copy, Paste and Select All in the window's Edit menu. */
  private final class TerminalEditHandler extends EditHandler {
    @Override
    public void computeEnabled() {
      for (final var item : LogisimMenuBar.EDIT_ITEMS) {
        setEnabled(
            item,
            item == LogisimMenuBar.COPY
                || item == LogisimMenuBar.PASTE
                || item == LogisimMenuBar.SELECT_ALL);
      }
    }

    @Override
    public void copy() {
      textArea.copy();
    }

    @Override
    public void paste() {
      SerialTerminal.this.paste();
    }

    @Override
    public void selectAll() {
      textArea.selectAll();
    }
  }

  private static final class TerminalMenuListener extends MenuListener {
    TerminalMenuListener(LogisimMenuBar menubar, EditHandler handler) {
      super(menubar);
      editListener.register();
      setEditHandler(handler);
    }
  }

  /** An open port, with a thread that reads it and one that writes to it. */
  private static final class Connection {
    /** A read waits this long (ms) for a byte; the writer looks for something to send as often. */
    private static final int TIMEOUT_MILLIS = 200;

    /**
     * A read that returns nothing at once, this many times in a row, means the port was hung up
     * (when the other end of a pseudo-terminal closes, for example); a port that is there waits.
     */
    private static final int HUNG_UP_READS = 10;

    private final SerialPort port;
    private final String path;
    private final String name;
    private final int speed;
    private final LinkedBlockingQueue<Chunk> toSend = new LinkedBlockingQueue<>();
    private volatile boolean closing;
    /** Whether the port and speed are saved as the ones to offer next time (on the EDT). */
    private boolean remembered;

    private record Chunk(byte[] bytes, boolean paced) {}

    Connection(String path, String name, int speed) throws IOException {
      this.path = path;
      this.name = name;
      this.speed = speed;
      try {
        port = SerialPort.getCommPort(path);
      } catch (SerialPortInvalidPortException e) {
        throw new IOException(S.get("serialTerminalNoSuchPort"), e);
      }
      port.setComPortParameters(speed, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
      port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
      port.setComPortTimeouts(
          SerialPort.TIMEOUT_READ_SEMI_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
          TIMEOUT_MILLIS,
          10 * TIMEOUT_MILLIS);
      if (!port.openPort()) {
        throw new IOException(openError(port.getLastErrorCode()));
      }
    }

    /** Why the port did not open, from the system's error code. */
    private static String openError(int code) {
      final var windows = System.getProperty("os.name", "").startsWith("Windows");
      if (windows ? code == 5 : code == 16 || code == 11 || code == 35) {
        return S.get("serialTerminalInUse"); // access denied; busy; locked (would block)
      }
      if (!windows && code == 13) {
        return S.get("serialTerminalNoPermission");
      }
      if (code == 2) {
        return S.get("serialTerminalNoSuchPort");
      }
      return S.get("serialTerminalError", code);
    }

    void start(Consumer<byte[]> received, Consumer<Connection> lost) {
      final var reader = new Thread(() -> read(received, lost), "Serial terminal reader");
      final var writer = new Thread(() -> write(lost), "Serial terminal writer");
      reader.setDaemon(true);
      writer.setDaemon(true);
      reader.start();
      writer.start();
    }

    void send(byte[] bytes, boolean paced) {
      toSend.add(new Chunk(bytes, paced));
    }

    void close() {
      closing = true;
      port.closePort(); // the threads stop within their timeouts
    }

    private void read(Consumer<byte[]> received, Consumer<Connection> lost) {
      final var buffer = new byte[4096];
      var emptyReads = 0;
      while (!closing) {
        final var start = System.nanoTime();
        final var count = port.readBytes(buffer, buffer.length);
        final var atOnce = System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS) / 4;
        if (count > 0) {
          emptyReads = 0;
          received.accept(Arrays.copyOf(buffer, count));
        } else if (count < 0 || atOnce && ++emptyReads >= HUNG_UP_READS) {
          if (!closing) {
            lost.accept(this);
          }
          return;
        } else if (!atOnce) {
          emptyReads = 0;
        }
      }
    }

    private void write(Consumer<Connection> lost) {
      try {
        while (!closing) {
          final var chunk = toSend.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
          if (chunk != null && !(chunk.paced() ? writePaced(chunk.bytes()) : writeAll(chunk.bytes()))) {
            if (!closing) {
              lost.accept(this);
            }
            return;
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    private boolean writeAll(byte[] bytes) {
      return port.writeBytes(bytes, bytes.length) == bytes.length;
    }

    private boolean writePaced(byte[] bytes) throws InterruptedException {
      for (final var b : bytes) {
        if (closing) {
          return true;
        }
        if (!writeAll(new byte[] {b})) {
          return false;
        }
        Thread.sleep(b == '\r' ? PASTE_LINE_MILLIS : PASTE_CHARACTER_MILLIS);
      }
      return true;
    }
  }
}
