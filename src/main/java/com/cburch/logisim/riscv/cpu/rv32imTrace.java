/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.cpu;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A record of the CPU's clock edges, to check its HDL against: with the Java system property rv32im.trace naming a
 * file, every rising edge of the CPU's clock adds 16 bytes to it, little-endian: what the CPU took at the edge (Data
 * In as the step took it, 4 bytes, so 0xffffffff when the bus was undefined; and a byte: bit 0 the timer interrupt,
 * 1 the external interrupt, 2 Bus Request), then what it gives
 * after the edge, as with a separate data bus (Address and Data Out, 4 bytes each, and a byte: bit 0 MemRead,
 * 1 MemWrite, 2-5 the byte enables 0-3, 6 Bus Ack), and 2 bytes of 0. The first record has what the CPU gave before
 * its first edge, with inputs 0. One CPU (the first that ticks) is recorded.
 */
final class rv32imTrace {

  private static final String FILE = System.getProperty("rv32im.trace");
  private static OutputStream out;
  private static Object recorded;
  private static long records;

  private rv32imTrace() {}

  static boolean on() {
    return FILE != null;
  }

  /** Outputs: address, data out and flags, as rv32im gives them with a separate data bus. */
  static synchronized void record(Object cpu, long[] before, long dataIn, int inFlags, long[] after) {
    try {
      if (out == null) {
        out = new BufferedOutputStream(new FileOutputStream(FILE), 1 << 16);
        recorded = cpu;
        write(0, 0, before);
      }
      if (cpu != recorded) return;
      write(dataIn, inFlags, after);
      if ((++records & 0xFFFF) == 0) out.flush();   // Logisim may be killed rather than closed
    } catch (IOException e) {
      throw new IllegalStateException("rv32im.trace: " + e.getMessage(), e);
    }
  }

  private static void write(long dataIn, int inFlags, long[] outputs) throws IOException {
    final var bytes = new byte[16];
    put(bytes, 0, dataIn);
    bytes[4] = (byte) inFlags;
    put(bytes, 5, outputs[0]);
    put(bytes, 9, outputs[1]);
    bytes[13] = (byte) outputs[2];
    out.write(bytes);
  }

  private static void put(byte[] bytes, int at, long value) {
    for (var i = 0; i < 4; i++) bytes[at + i] = (byte) (value >>> (8 * i));
  }
}
