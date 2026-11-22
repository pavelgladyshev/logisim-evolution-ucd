/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.download;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * nextpnr-himbaechel's report of what a circuit takes of the FPGA, which ends the Gowin flow when the circuit needs
 * more of a resource than the FPGA has (GowinDownload.checkOutput). The lines are nextpnr's for a Tang Nano 9K.
 */
class GowinDownloadTest {

  @Test
  void tooFewOfAResourceEndsTheBuild() {
    assertArrayEquals(new String[] {"LUT4", "8997", "8640"},
        GowinDownload.excess("Info: \t                LUT4:    8997/   8640   104%"));
  }

  @Test
  void otherLinesLetItGoOn() {
    assertNull(GowinDownload.excess("Info: \t                LUT4:    2798/   8640    32%"));
    assertNull(GowinDownload.excess("Info: \t               BSRAM:      26/     26   100%"));
    assertNull(GowinDownload.excess("Info: Device utilisation:"));
    assertNull(GowinDownload.excess("Warning: Max frequency for clock 'clk': 10.78 MHz (FAIL at 27.00 MHz)"));
  }
}
