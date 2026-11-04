/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The FPGA clocks that a clock cycle gets at a tick frequency, and the highest tick frequency for a
 * component that needs so many (DownloadBase.checkFpgaClocksPerCycle).
 */
class DownloadClockCycleTest {

  private static final long TANG_NANO_20K = 27_000_000L;

  @Test
  void cyclesOnTheTangNano20K() {
    // two ticks a cycle, at Logisim's 4096, 2048 and 1024 kHz, and the CPU's highest
    assertEquals(12, DownloadBase.fpgaClocksPerCycle(TANG_NANO_20K, 4_096_000, 2));
    assertEquals(26, DownloadBase.fpgaClocksPerCycle(TANG_NANO_20K, 2_048_000, 2));
    assertEquals(52, DownloadBase.fpgaClocksPerCycle(TANG_NANO_20K, 1_024_000, 2));
    assertEquals(24, DownloadBase.fpgaClocksPerCycle(TANG_NANO_20K, 2_250_000, 2));
    // a Clock of one tick high and two low
    assertEquals(36, DownloadBase.fpgaClocksPerCycle(TANG_NANO_20K, 2_250_000, 3));
  }

  @Test
  void highestTickFrequencies() {
    assertEquals(2_250_000, DownloadBase.highestTickFrequency(TANG_NANO_20K, 23, 2));
    assertEquals(4_500_000, DownloadBase.highestTickFrequency(TANG_NANO_20K, 12, 2));
    assertEquals(3_375_000, DownloadBase.highestTickFrequency(TANG_NANO_20K, 23, 3));
  }

  @Test
  void frequencyStrings() {
    assertEquals("27 MHz", DownloadBase.frequencyString(27_000_000));
    assertEquals("2.25 MHz", DownloadBase.frequencyString(2_250_000));
    assertEquals("2.076 MHz", DownloadBase.frequencyString(27_000_000 / 13.0));
    assertEquals("512 kHz", DownloadBase.frequencyString(512_000));
    assertEquals("100 Hz", DownloadBase.frequencyString(100));
  }

  @Test
  void theFrequencyTheMessageGivesIsLowEnough() {
    // rounded down for the message, and typed in as it reads, it still gives enough FPGA clocks
    for (final var fpgaClock : new long[] {12_000_000L, 27_000_000L, 50_000_000L, 100_000_000L}) {
      for (final var needed : new int[] {12, 23, 40}) {
        for (final var ticks : new int[] {2, 3, 5}) {
          final var shown =
              DownloadBase.frequencyString(
                  DownloadBase.highestTickFrequency(fpgaClock, needed, ticks));
          final var cycle = DownloadBase.fpgaClocksPerCycle(fpgaClock, typedIn(shown), ticks);
          assertTrue(cycle >= needed, fpgaClock + " Hz, " + needed + " clocks: " + shown);
        }
      }
    }
  }

  /** The frequency as the FPGA window reads a typed one (FpgaClockPanel.getTickFrequency). */
  private static double typedIn(String shown) {
    final var parts = shown.split(" ");
    var scale = 1.0;
    if ("MHz".equals(parts[1])) {
      scale = 1e6;
    } else if ("kHz".equals(parts[1])) {
      scale = 1e3;
    }
    return Double.parseDouble(parts[0]) * scale;
  }
}
