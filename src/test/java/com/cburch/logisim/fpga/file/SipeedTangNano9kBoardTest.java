/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.fpga.data.BoardInformation;
import com.cburch.logisim.fpga.data.FpgaIoInformationContainer;
import com.cburch.logisim.fpga.data.IoComponentTypes;
import com.cburch.logisim.fpga.data.IoStandards;
import com.cburch.logisim.fpga.data.PinActivity;
import com.cburch.logisim.fpga.data.PullBehaviors;
import com.cburch.logisim.fpga.settings.VendorSoftware;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Sipeed Tang Nano 9K's board file against Sipeed's schematic (Tang_nano_9K_3674): the FPGA as the Gowin flow
 * names it, the pins of the clock, buttons, LEDs and USB-UART, and the 1.8 V bank.
 */
class SipeedTangNano9kBoardTest {

  /** Bank 3, at 1.8 V on this board (VCCO3_1V8): the buttons, the LEDs and the header pins 79-86. */
  private static final Set<String> BANK3 =
      Set.of("3", "4", "10", "11", "13", "14", "15", "16", "79", "80", "81", "82", "83", "84", "85", "86");

  /** The TF slot's lines, on the picture as TF_* and as header pins too. */
  private static final Set<String> TF_PINS = Set.of("36", "37", "38", "39");

  private static BoardInformation board;

  @BeforeAll
  static void read() {
    board = new BoardReaderClass("url:resources/logisim/boards/SIPEED_TANG_NANO_9K.xml").getBoardInformation();
    assertNotNull(board);
  }

  private static FpgaIoInformationContainer component(String label) {
    for (final var comp : board.getAllComponents()) {
      if (label.equals(comp.getLabel())) return comp;
    }
    throw new AssertionError("no " + label + " on the board");
  }

  @Test
  void fpgaAsTheGowinFlowNamesIt() {
    final var fpga = board.fpga;
    assertEquals("SIPEED_TANG_NANO_9K", board.getBoardName());
    assertEquals(VendorSoftware.VENDOR_GOWIN, fpga.getVendor());
    // GowinDownload: the nextpnr-himbaechel device and the family it and gowin_pack take
    assertEquals("GW1NR-LV9QN88PC6/I5", fpga.getPart() + fpga.getPackage() + fpga.getSpeedGrade());
    assertEquals("GW1N-9C", fpga.getTechnology());
    assertEquals("52", fpga.getClockPinLocation());
    assertEquals(27_000_000L, fpga.getClockFrequency());
    assertTrue(fpga.isFlashDefined(), "Write to flash: the FPGA's internal flash");
    assertEquals(740, board.getImage().getWidth());
    assertEquals(400, board.getImage().getHeight());
  }

  @Test
  void buttonsLedsAndUart() {
    for (final var button : new String[] {"S1", "S2"}) {
      final var comp = component(button);
      assertEquals(IoComponentTypes.Button, comp.getType());
      assertEquals(PinActivity.ACTIVE_LOW, comp.getActivityLevel(), button + " pulls its pin to ground");
      assertEquals(PullBehaviors.PULL_UP, comp.getPullBehavior());
    }
    // as printed on the board and named on the switches in the schematic, whose net names have them swapped
    assertEquals("4", component("S1").getPinLocation(0));
    assertEquals("3", component("S2").getPinLocation(0));
    final var ledPins = new String[] {"10", "11", "13", "14", "15", "16"};
    for (var i = 0; i < ledPins.length; i++) {
      final var led = component("LED" + i);
      assertEquals(IoComponentTypes.Led, led.getType());
      assertEquals(ledPins[i], led.getPinLocation(0));
      assertEquals(PinActivity.ACTIVE_LOW, led.getActivityLevel(), "the LEDs' anodes are at 1.8 V");
    }
    assertEquals("17", component("UART_TX").getPinLocation(0));
    assertEquals("18", component("UART_RX").getPinLocation(0));
  }

  @Test
  void headerPinsAndTheirBanks() {
    final var headers = new TreeSet<Integer>();
    final var uses = new HashMap<String, Integer>();
    for (final var comp : board.getAllComponents()) {
      for (var i = 0; i < comp.getNrOfPins(); i++) {
        final var pin = comp.getPinLocation(i);
        uses.merge(pin, 1, Integer::sum);
        final char standard = BANK3.contains(pin) ? IoStandards.LVCMOS18 : IoStandards.LVCMOS33;
        assertEquals(standard, comp.getIoStandard(),
            comp.getLabel() + " on pin " + pin + ": " + IoStandards.BEHAVIOR_STRINGS[standard]);
      }
      if (comp.getType() == IoComponentTypes.PortIo && comp.getLabel().matches("P\\d+")) {
        assertEquals(comp.getLabel().substring(1), comp.getPinLocation(0));
        headers.add(Integer.parseInt(comp.getPinLocation(0)));
      }
    }
    // the two 24-pin headers less 3V3, GND and 5V
    assertEquals(45, headers.size());
    assertEquals(new TreeSet<>(Set.of(25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 48,
        49, 51, 53, 54, 55, 56, 57, 63, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 79, 80, 81, 82, 83, 84, 85, 86)),
        headers);
    for (final Map.Entry<String, Integer> use : uses.entrySet()) {
      final var expected = TF_PINS.contains(use.getKey()) ? 2 : 1;
      assertEquals(expected, use.getValue(), "pin " + use.getKey() + " on the picture");
    }
  }
}
