/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The WSL distributions JFileChoosers finds in the output of reg.exe (Windows' registry). */
class JFileChoosersTest {
  private static final String KEY =
      "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss\\";

  /** reg.exe's output for this many distributions, with Windows' line ends. */
  private static String regOutput(String... names) {
    final var out = new StringBuilder("\r\n");
    for (var i = 0; i < names.length; i++) {
      out.append(KEY).append("{a1b2c3d4-0000-4000-8000-00000000000").append(i).append("}\r\n");
      out.append("    DistributionName    REG_SZ    ").append(names[i]).append("\r\n\r\n");
    }
    return out.append("End of search: ").append(names.length).append(" match(es) found.\r\n")
        .toString();
  }

  @Test
  void findsTheDistributions() {
    assertEquals(List.of("Ubuntu-24.04", "Debian"),
        JFileChoosers.wslDistributions(regOutput("Ubuntu-24.04", "Debian")));
  }

  @Test
  void leavesOutDockerAndRancherDesktopsOwn() {
    assertEquals(List.of("Ubuntu"), JFileChoosers.wslDistributions(regOutput(
        "docker-desktop", "Ubuntu", "docker-desktop-data", "rancher-desktop")));
  }

  @Test
  void namesEachDistributionOnce() {
    assertEquals(List.of("Ubuntu"),
        JFileChoosers.wslDistributions(regOutput("Ubuntu", "Ubuntu")));
  }

  @Test
  void leavesOutWhatIsNotADistributionName() {
    // the names go into a path: only what WSL allows in one
    assertEquals(List.of("kali-linux"),
        JFileChoosers.wslDistributions(regOutput("my distro", "kali-linux", "a\\b")));
  }

  @Test
  void noWslNoDistributions() {
    assertEquals(List.of(), JFileChoosers.wslDistributions(""));
    assertEquals(List.of(), JFileChoosers.wslDistributions(
        "ERROR: The system was unable to find the specified registry key or value.\r\n"));
  }
}
