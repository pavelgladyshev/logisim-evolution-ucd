/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.gui;

/**
 * The text of the {@link SerialTerminal}: what a board sends, shown as a terminal shows it. It
 * understands what the TTY component sends on an FPGA (see TtyHdlGeneratorFactory) and a little
 * more:
 *
 * <ul>
 *   <li>bytes are ISO-8859-1 characters; the cursor overwrites the characters it is on;
 *   <li>CR goes to the start of the line, LF to the start of a new line, BS one character back (so
 *       BS SP BS erases one), and TAB to the next multiple of 8;
 *   <li>FF, ESC [2J and ESC c clear the screen; ESC [K and ESC [J erase to the end of the line; ESC
 *       [nC, ESC [nD, ESC [nG and ESC [r;cH move the cursor along the line;
 *   <li>other control characters and escape sequences are ignored.
 * </ul>
 *
 * <p>Only the last line can change, as the cursor cannot move up. When the text grows beyond {@link
 * #MAX_TEXT} characters, the oldest lines go, leaving about the last {@link #KEEP_TEXT}.
 *
 * <p>The window copies the text into its text area: {@link #takeChangedFrom} says from where it
 * changed since the last time.
 */
final class TerminalScreen {
  static final int MAX_TEXT = 1_000_000;
  static final int KEEP_TEXT = MAX_TEXT / 2;
  /** A longer line goes on in a new line (a board printing without new lines). */
  static final int MAX_LINE = 10_000;

  private static final int TAB = 8;
  private static final int MAX_PARAMETERS = 32;

  private enum Escape {
    NONE,
    ESC,
    CSI
  }

  private final StringBuilder text = new StringBuilder();
  private final StringBuilder parameters = new StringBuilder();
  private int lineStart;
  private int column;
  private int changedFrom = -1;
  private Escape escape = Escape.NONE;

  /** Shows these bytes. */
  void write(byte[] data, int offset, int length) {
    for (var i = offset; i < offset + length; i++) {
      put((char) (data[i] & 0xFF));
    }
    if (text.length() > MAX_TEXT) {
      dropOldLines();
    }
  }

  /** Clears the screen. */
  void clear() {
    text.setLength(0);
    lineStart = 0;
    column = 0;
    escape = Escape.NONE;
    changed(0);
  }

  /** All of the text. */
  String getText() {
    return text.toString();
  }

  /** The text from this offset on. */
  String getText(int from) {
    return text.substring(from);
  }

  /** Where the cursor is in the text. */
  int getCursor() {
    return lineStart + column;
  }

  /**
   * The first offset in the text that changed since the last call (the text before it is as it
   * was), or -1 if nothing changed.
   */
  int takeChangedFrom() {
    final var from = changedFrom;
    changedFrom = -1;
    return from;
  }

  private void put(char c) {
    switch (escape) {
      case ESC -> {
        if (c == '[') {
          escape = Escape.CSI;
          parameters.setLength(0);
        } else if (c < 0x20 || c > 0x2F) {
          // the end of the sequence (0x20-0x2F come between ESC and its end); a control
          // character ends it and does what it does
          escape = Escape.NONE;
          if (c == 'c') {
            clear();
          } else if (c < 0x20) {
            put(c);
          }
        }
      }
      case CSI -> {
        if (c >= 0x40 && c <= 0x7E) {
          escape = Escape.NONE;
          controlSequence(c, parameters.toString());
        } else if (c >= 0x20 && c <= 0x3F) {
          if (parameters.length() < MAX_PARAMETERS) {
            parameters.append(c);
          }
        } else {
          escape = Escape.NONE;
          put(c);
        }
      }
      default -> {
        if (c == 0x1B) {
          escape = Escape.ESC;
        } else if (c >= 0x20 && c != 0x7F && (c < 0x80 || c >= 0xA0)) {
          printable(c);
        } else {
          control(c);
        }
      }
    }
  }

  private void control(char c) {
    switch (c) {
      case '\r' -> column = 0;
      case '\n' -> newLine();
      case '\b' -> column = Math.max(0, column - 1);
      case '\t' -> moveTo((column / TAB + 1) * TAB);
      case '\f' -> clear();
      default -> {
        // ignored, as the TTY component ignores it
      }
    }
  }

  private void printable(char c) {
    if (column >= MAX_LINE) {
      newLine();
    }
    final var at = lineStart + column;
    if (at < text.length()) {
      text.setCharAt(at, c);
    } else {
      text.append(c);
    }
    changed(at);
    column++;
  }

  private void newLine() {
    // trailing spaces (left by BS SP BS) would only get into what is copied
    var end = text.length();
    while (end > lineStart && text.charAt(end - 1) == ' ') {
      end--;
    }
    text.setLength(end);
    changed(end);
    text.append('\n');
    lineStart = text.length();
    column = 0;
  }

  /** Moves the cursor to this column of the line, adding spaces after its end. */
  private void moveTo(int newColumn) {
    final var target = Math.min(Math.max(newColumn, 0), MAX_LINE);
    final var length = text.length() - lineStart;
    if (target > length) {
      changed(text.length());
      text.append(" ".repeat(target - length));
    }
    column = target;
  }

  private void eraseToEndOfLine() {
    final var at = lineStart + column;
    if (at < text.length()) {
      text.setLength(at);
      changed(at);
    }
  }

  private void controlSequence(char command, String parameters) {
    switch (command) {
      case 'J' -> {
        if (parameters.equals("2") || parameters.equals("3")) {
          clear();
        } else if (parameters.isEmpty() || parameters.equals("0")) {
          eraseToEndOfLine();
        }
      }
      case 'K' -> {
        if (parameters.isEmpty() || parameters.equals("0")) {
          eraseToEndOfLine();
        }
      }
      case 'C' -> moveTo(column + count(parameters, 0));
      case 'D' -> column = Math.max(0, column - count(parameters, 0));
      case 'G' -> moveTo(count(parameters, 0) - 1);
      case 'H', 'f' -> moveTo(count(parameters, 1) - 1);
      default -> {
        // colours, cursor up and down, modes, ...: ignored
      }
    }
  }

  /** The number at this position in "n;m" (at least 1; 1 when it is missing). */
  private static int count(String parameters, int position) {
    final var numbers = parameters.split(";", -1);
    if (position >= numbers.length) {
      return 1;
    }
    try {
      return Math.max(1, Math.min(Integer.parseInt(numbers[position].trim()), MAX_LINE));
    } catch (NumberFormatException e) {
      return 1;
    }
  }

  private void dropOldLines() {
    // from the first line that starts in the last KEEP_TEXT characters (the last line is shorter)
    final var newLine = text.indexOf("\n", text.length() - KEEP_TEXT);
    final var cut = newLine < 0 ? lineStart : newLine + 1;
    if (cut > 0) {
      text.delete(0, cut);
      lineStart -= cut;
      changed(0);
    }
  }

  private void changed(int at) {
    if (changedFrom < 0 || at < changedFrom) {
      changedFrom = at;
    }
  }
}
