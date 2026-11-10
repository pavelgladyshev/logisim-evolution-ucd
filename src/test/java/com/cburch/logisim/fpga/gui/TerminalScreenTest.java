/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TerminalScreenTest {

  private static TerminalScreen screen(String... writes) {
    final var screen = new TerminalScreen();
    for (final var text : writes) {
      write(screen, text);
    }
    return screen;
  }

  private static void write(TerminalScreen screen, String text) {
    final var bytes = text.getBytes(StandardCharsets.ISO_8859_1);
    screen.write(bytes, 0, bytes.length);
  }

  @Test
  void printableTextIsShownWithTheCursorAfterIt() {
    final var screen = screen("Hello");
    assertEquals("Hello", screen.getText());
    assertEquals(5, screen.getCursor());
  }

  @Test
  void crLfAsTheTtySendsItStartsANewLine() {
    final var screen = screen("Welcome\r\n/$ ");
    assertEquals("Welcome\n/$ ", screen.getText());
    assertEquals(screen.getText().length(), screen.getCursor());
  }

  @Test
  void lfAloneStartsANewLineToo() {
    assertEquals("a\nb", screen("a\nb").getText());
  }

  @Test
  void crGoesBackAndTheCursorOverwrites() {
    final var screen = screen("abc\rX");
    assertEquals("Xbc", screen.getText());
    assertEquals(1, screen.getCursor());
  }

  @Test
  void bsSpBsErasesTheLastCharacter() {
    final var screen = screen("/$ lx\b \b");
    assertEquals("/$ l ", screen.getText());
    assertEquals(4, screen.getCursor());
    write(screen, "s\r\n");
    assertEquals("/$ ls\n", screen.getText());
  }

  @Test
  void trailingSpacesGoAtTheEndOfALine() {
    assertEquals("ab\nc", screen("abc\b \b\r\nc").getText());
  }

  @Test
  void backspaceStopsAtTheStartOfTheLine() {
    final var screen = screen("a\r\n\b\bb");
    assertEquals("a\nb", screen.getText());
  }

  @Test
  void tabGoesToTheNextMultipleOf8() {
    final var screen = screen("a\tb");
    assertEquals("a       b", screen.getText());
    assertEquals(9, screen.getCursor());
  }

  @Test
  void formFeedAndTheTtysClearSequenceClearTheScreen() {
    assertEquals("x", screen("one\r\ntwo\fx").getText());
    final var screen = screen("one\r\ntwo\u001b[2J\u001b[Hx");
    assertEquals("x", screen.getText());
    assertEquals(1, screen.getCursor());
    assertEquals("y", screen("old\u001bcy").getText());
  }

  @Test
  void eraseToTheEndOfTheLine() {
    assertEquals("abc", screen("abcdef\r\u001b[3C\u001b[K").getText());
    assertEquals("ab", screen("abcd\b\b\u001b[J").getText());
  }

  @Test
  void cursorMovesAlongTheLine() {
    assertEquals("aXc", screen("abc\u001b[2DX").getText());
    assertEquals("abcX", screen("abc\u001b[1GabcX").getText());
    assertEquals("ab  X", screen("ab\u001b[1;5HX").getText());
  }

  @Test
  void otherEscapeSequencesAreIgnored() {
    assertEquals("red", screen("\u001b[1;31mred\u001b[0m").getText());
    assertEquals("ab", screen("a\u001b[?25lb").getText());
    assertEquals("xy", screen("x\u001b(By").getText());
    assertEquals("a\nb", screen("a\u001b\nb").getText());
  }

  @Test
  void anEscapeSequenceMaySpanWrites() {
    assertEquals("new", screen("old\u001b", "[2", "Jnew").getText());
  }

  @Test
  void otherControlCharactersAreIgnored() {
    assertEquals("ab", screen("a\u0007\u0000\u007f\u0085b").getText());
  }

  @Test
  void bytesAreIso88591Characters() {
    final var screen = new TerminalScreen();
    screen.write(new byte[] {'c', 'a', 'f', (byte) 0xE9}, 0, 4);
    assertEquals("café", screen.getText());
  }

  @Test
  void writeTakesThePartOfTheArrayAsked() {
    final var screen = new TerminalScreen();
    screen.write("xxabcxx".getBytes(StandardCharsets.ISO_8859_1), 2, 3);
    assertEquals("abc", screen.getText());
  }

  @Test
  void theTextAreaIsToldFromWhereTheTextChanged() {
    final var screen = screen("abc");
    assertEquals(0, screen.takeChangedFrom());
    assertEquals(-1, screen.takeChangedFrom());
    write(screen, "d\r\n");
    assertEquals(3, screen.takeChangedFrom());
    write(screen, "xy\rZ");
    assertEquals(5, screen.takeChangedFrom());
    assertEquals("abcd\nZy", screen.getText());
    write(screen, "\r");
    assertEquals(-1, screen.takeChangedFrom());
    screen.clear();
    assertEquals(0, screen.takeChangedFrom());
    assertEquals("", screen.getText());
  }

  @Test
  void theOldestLinesGoWhenTheTextIsTooLong() {
    final var screen = new TerminalScreen();
    final var line = "0123456789".repeat(9) + "\r\n"; // 91 characters of text
    var shown = 0;
    var trimmed = false;
    screen.takeChangedFrom();
    while (shown <= TerminalScreen.MAX_TEXT) {
      write(screen, line);
      shown += line.length() - 1;
      trimmed |= screen.takeChangedFrom() == 0; // the text area copies all of it again
    }
    assertTrue(trimmed);
    write(screen, "/$ ");
    final var text = screen.getText();
    assertTrue(text.length() <= TerminalScreen.KEEP_TEXT + line.length(), "kept " + text.length());
    assertTrue(text.length() >= TerminalScreen.KEEP_TEXT - line.length(), "kept " + text.length());
    assertTrue(text.startsWith("0123456789"), "starts with a whole line");
    assertTrue(text.endsWith("89\n/$ "));
    assertEquals(text.length(), screen.getCursor());
  }

  @Test
  void veryLongLineGoesOnInANewLine() {
    final var screen = screen("x".repeat(TerminalScreen.MAX_LINE + 5));
    final var text = screen.getText();
    assertEquals(TerminalScreen.MAX_LINE, text.indexOf('\n'));
    assertEquals(TerminalScreen.MAX_LINE + 1 + 5, text.length());
  }
}
