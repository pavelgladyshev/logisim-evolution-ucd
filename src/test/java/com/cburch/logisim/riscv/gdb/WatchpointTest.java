/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.gdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.riscv.cpu.gdb.Watchpoint;
import com.cburch.logisim.riscv.cpu.gdb.Watchpoint.Kind;
import org.junit.jupiter.api.Test;

/** Watchpoints: which accesses hit them (Z2 stores, Z3 loads, Z4 both), and the stop reply. */
class WatchpointTest {

  @Test
  void packetTypes() {
    assertEquals(Kind.WRITE, Kind.ofPacketType(2));
    assertEquals(Kind.READ, Kind.ofPacketType(3));
    assertEquals(Kind.ACCESS, Kind.ofPacketType(4));
    assertNull(Kind.ofPacketType(0));
    assertNull(Kind.ofPacketType(5));
  }

  @Test
  void kinds() {
    final var write = new Watchpoint(Kind.WRITE, 0x100000, 4);
    final var read = new Watchpoint(Kind.READ, 0x100000, 4);
    final var access = new Watchpoint(Kind.ACCESS, 0x100000, 4);
    assertTrue(write.hits(0x100000, 4, true));
    assertFalse(write.hits(0x100000, 4, false));
    assertTrue(read.hits(0x100000, 4, false));
    assertFalse(read.hits(0x100000, 4, true));
    assertTrue(access.hits(0x100000, 4, false));
    assertTrue(access.hits(0x100000, 4, true));
  }

  @Test
  void overlap() {
    final var w = new Watchpoint(Kind.WRITE, 0x100004, 4);
    assertTrue(w.hits(0x100007, 1, true));
    assertTrue(w.hits(0x100002, 4, true));     // sw at 0x100002 is misaligned, but a halfword at 0x100003 is not
    assertTrue(w.hits(0x100006, 2, true));
    assertFalse(w.hits(0x100008, 4, true));
    assertFalse(w.hits(0x100000, 4, true));
    assertFalse(w.hits(0x100003, 1, true));
  }

  @Test
  void stopReason() {
    assertEquals("watch:100004;", new Watchpoint(Kind.WRITE, 0x100004, 4).stopReason(0x100004));
    assertEquals("rwatch:100006;", new Watchpoint(Kind.READ, 0x100004, 4).stopReason(0x100006));
    assertEquals("awatch:100004;", new Watchpoint(Kind.ACCESS, 0x100004, 4).stopReason(0x100000),
        "an address inside the range, for an access that starts before it");
  }

  @Test
  void equalityForRemoval() {
    assertEquals(new Watchpoint(Kind.WRITE, 0x10, 4), new Watchpoint(Kind.WRITE, 0x10, 4));
    assertFalse(new Watchpoint(Kind.WRITE, 0x10, 4).equals(new Watchpoint(Kind.READ, 0x10, 4)));
    assertFalse(new Watchpoint(Kind.WRITE, 0x10, 4).equals(new Watchpoint(Kind.WRITE, 0x10, 2)));
  }
}
