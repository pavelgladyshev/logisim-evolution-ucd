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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.std.io.TtyHdlGeneratorFactory;
import com.cburch.logisim.util.LocaleManager;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SerialTerminalTest {

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.ISO_8859_1);
  }

  @Test
  void keysAreSentAsATerminalSendsThem() {
    assertArrayEquals(new byte[] {'\r'}, SerialTerminal.bytesForKey('\n'));
    assertArrayEquals(new byte[] {'\r'}, SerialTerminal.bytesForKey('\r'));
    assertArrayEquals(new byte[] {0x7F}, SerialTerminal.bytesForKey('\b'));
    assertArrayEquals(new byte[] {0x7F}, SerialTerminal.bytesForKey((char) 0x7F));
    assertArrayEquals(new byte[] {'\t'}, SerialTerminal.bytesForKey('\t'));
    assertArrayEquals(new byte[] {0x1B}, SerialTerminal.bytesForKey((char) 0x1B));
    assertArrayEquals(new byte[] {'a'}, SerialTerminal.bytesForKey('a'));
    assertArrayEquals(new byte[] {' '}, SerialTerminal.bytesForKey(' '));
    assertArrayEquals(new byte[] {(byte) 0xE9}, SerialTerminal.bytesForKey('é'));
  }

  @Test
  void keysWithoutAByteAreNotSent() {
    assertArrayEquals(new byte[0], SerialTerminal.bytesForKey('€'));
    assertArrayEquals(new byte[0], SerialTerminal.bytesForKey((char) 3)); // Ctrl+C: Copy
    assertArrayEquals(new byte[0], SerialTerminal.bytesForKey((char) 0x16)); // Ctrl+V: Paste
    assertArrayEquals(new byte[0], SerialTerminal.bytesForKey((char) 0x85));
  }

  @Test
  void pasteIsTypedWithCrForEachNewLine() {
    assertArrayEquals(bytes("ls /\recho hi\rx\ry"), SerialTerminal.bytesForPaste("ls /\necho hi\r\nx\ry"));
    assertArrayEquals(bytes("a\tb"), SerialTerminal.bytesForPaste("a\tb\u0007"));
    assertArrayEquals(bytes("café "), SerialTerminal.bytesForPaste("café €"));
    assertArrayEquals(bytes("\r\r"), SerialTerminal.bytesForPaste("\r\n\n"));
  }

  @Test
  void theBoardsUartComesFirst() {
    // the Tang Nano 20K on a Mac: JTAG ...0, UART ...1
    assertEquals(3, SerialTerminal.boardScore("cu.usbserial-20250303171", 0x0403));
    assertEquals(2, SerialTerminal.boardScore("cu.usbserial-20250303170", 0x0403));
    assertEquals(3, SerialTerminal.boardScore("ttyUSB1", 0x0403));
    assertEquals(2, SerialTerminal.boardScore("ttyUSB0", -1));
    assertEquals(2, SerialTerminal.boardScore("ttyACM0", -1));
    assertEquals(2, SerialTerminal.boardScore("COM7", 0x0403));
    assertEquals(0, SerialTerminal.boardScore("COM1", -1));
    assertEquals(0, SerialTerminal.boardScore("cu.Bluetooth-Incoming-Port", -1));
    assertEquals(0, SerialTerminal.boardScore("cu.debug-console", -1));
  }

  private static SerialTerminal.PortInfo usb(String name, String serialNumber) {
    return new SerialTerminal.PortInfo("/dev/" + name, name, "USB Debugger", 0x0403, serialNumber);
  }

  private static SerialTerminal.PortInfo other(String name) {
    return new SerialTerminal.PortInfo("/dev/" + name, name, name, -1, "Unknown");
  }

  private static List<String> names(List<SerialTerminal.PortInfo> ports) {
    return SerialTerminal.choices(ports).stream().map(SerialTerminal.PortChoice::name).toList();
  }

  @Test
  void theBoardsJtagPortAndAMacsDialInPortsAreLeftOut() {
    // what jSerialComm said on a Mac with the Tang Nano 20K plugged in
    assertEquals(
        List.of("cu.usbserial-20250303171", "cu.Bluetooth-Incoming-Port", "cu.debug-console"),
        names(
            List.of(
                other("cu.debug-console"),
                other("tty.debug-console"),
                usb("cu.usbserial-20250303170", "2025030317"),
                usb("tty.usbserial-20250303170", "2025030317"),
                usb("cu.usbserial-20250303171", "2025030317"),
                usb("tty.usbserial-20250303171", "2025030317"),
                other("cu.Bluetooth-Incoming-Port"),
                other("tty.Bluetooth-Incoming-Port"))));
    assertEquals(List.of("ttyUSB1", "ttyS0"), names(List.of(other("ttyS0"), usb("ttyUSB0", "A"), usb("ttyUSB1", "A"))));
    assertEquals(List.of("ttyUSB10"), names(List.of(usb("ttyUSB10", "A"), usb("ttyUSB9", "A"))));
    assertEquals(List.of("COM7"), names(List.of(usb("COM6", "210328A9C4A1"), usb("COM7", "210328A9C4A1"))));
  }

  @Test
  void portsOfOtherChipsStay() {
    // after Zadig only the UART is a COM port; two boards; a one-port adapter; no serial numbers
    assertEquals(List.of("COM7", "COM1"), names(List.of(other("COM1"), usb("COM7", "2025030317"))));
    assertEquals(
        List.of("ttyUSB1", "ttyUSB3"),
        names(List.of(usb("ttyUSB0", "A"), usb("ttyUSB1", "A"), usb("ttyUSB2", "B"), usb("ttyUSB3", "B"))));
    assertEquals(List.of("ttyACM0"), names(List.of(usb("ttyACM0", "X1"))));
    assertEquals(List.of("ttyUSB1", "ttyUSB0"), names(List.of(usb("ttyUSB0", ""), usb("ttyUSB1", null))));
  }

  @Test
  void portIsListedWithWhatTheSystemCallsIt() {
    assertEquals(
        "COM7 - USB Serial Port",
        new SerialTerminal.PortChoice("\\\\.\\COM7", "COM7", "USB Serial Port", 2).toString());
    assertEquals(
        "cu.usbserial-20250303171 - USB Debugger",
        new SerialTerminal.PortChoice("/dev/cu.usbserial-20250303171", "cu.usbserial-20250303171", "USB Debugger", 3)
            .toString());
    assertEquals(
        "cu.debug-console",
        new SerialTerminal.PortChoice("/dev/cu.debug-console", "cu.debug-console", "debug-console", 0)
            .toString());
    assertEquals("ttyS0", new SerialTerminal.PortChoice("/dev/ttyS0", "ttyS0", "", 0).toString());
  }

  @Test
  void theSpeedsIncludeTheTtys() {
    assertTrue(Arrays.stream(SerialTerminal.SPEEDS).anyMatch(s -> s == TtyHdlGeneratorFactory.BAUD_RATE));
  }

  @Test
  void everyMessageIsTranslatedToEnglish() {
    final var originalLocale = LocaleManager.getLocale();
    try {
      LocaleManager.setLocale(Locale.ENGLISH);
      assertEquals("Serial Terminal…", S.get("FPGASerialTerminal"));
      assertEquals("Connected to cu.usbserial-1 at 115200 baud.", S.get("serialTerminalConnected", "cu.usbserial-1", 115200));
      assertEquals(
          "Cannot open COM7: another program is using it (picocom, screen, another Logisim?).",
          S.get("serialTerminalOpenFailed", "COM7", S.get("serialTerminalInUse")));
      for (final var key :
          new String[] {
            "serialTerminalClear", "serialTerminalConnect", "serialTerminalCopy", "serialTerminalDisconnect",
            "serialTerminalError", "serialTerminalLost", "serialTerminalNoBoard", "serialTerminalNoLibrary",
            "serialTerminalNoPermission", "serialTerminalNoSuchPort", "serialTerminalNotConnected",
            "serialTerminalPaste", "serialTerminalPort", "serialTerminalSelectAll", "serialTerminalSpeed",
            "serialTerminalTitle", "serialTerminalTypeNotConnected"
          }) {
        final var text = S.get(key);
        assertTrue(!text.isEmpty() && !text.contains(key), key + " = " + text);
      }
    } finally {
      LocaleManager.setLocale(originalLocale);
    }
  }
}
