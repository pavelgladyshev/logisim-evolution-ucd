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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.riscv.cpu.gdb.Packet;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** GDB remote protocol framing: $data#xx, xx being the sum of the bytes of data mod 256. */
class PacketTest {

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.ISO_8859_1);
  }

  private static Packet parse(String s) {
    byte[] b = bytes(s);
    return new Packet(b, b.length, 0);
  }

  @Test
  void checksumZeroIsValid() {
    // gdb sends this to read the instruction at a return address; its checksum happens to be 00
    Packet p = parse("$m104428,4#00");
    assertTrue(p.isValid());
    assertEquals("m104428,4", p.getPacketData());
    assertEquals(13, p.getProcessedBytes());
  }

  @Test
  void ordinaryPacketIsValid() {
    Packet p = parse("$g#67");
    assertTrue(p.isValid());
    assertEquals("g", p.getPacketData());
  }

  @Test
  void wrongChecksumIsInvalid() {
    assertFalse(parse("$g#66").isValid());
  }

  @Test
  void packetWithoutChecksumYetIsInvalid() {
    assertFalse(parse("$m104428,4#0").isValid());
    assertFalse(parse("$m104428,4").isValid());
  }

  @Test
  void emptyPacketIsValid() {
    Packet p = parse("$#00");
    assertTrue(p.isValid());
    assertEquals("", p.getPacketData());
  }

  @Test
  void singleBytesAreNotPackets() {
    Packet ack = parse("+");
    assertTrue(ack.isACK());
    assertFalse(ack.isValid());
    Packet nack = parse("-");
    assertTrue(nack.isNACK());
    assertFalse(nack.isValid());
    Packet ctrlC = parse("\u0003");
    assertTrue(ctrlC.isCtrlC());
    assertFalse(ctrlC.isValid());
  }

  @Test
  void ctrlCBeforeAPacketIsNotSkipped() {
    byte[] b = bytes("\u0003$g#67");
    Packet first = new Packet(b, b.length, 0);
    assertTrue(first.isCtrlC());
    assertEquals(1, first.getProcessedBytes());
    Packet second = new Packet(b, b.length, first.getProcessedBytes());
    assertTrue(second.isValid());
    assertEquals("g", second.getPacketData());
  }

  @Test
  void ackFollowedByPacketInOneRead() {
    byte[] b = bytes("+$m104428,4#00");
    Packet first = new Packet(b, b.length, 0);
    assertTrue(first.isACK());
    Packet second = new Packet(b, b.length, first.getProcessedBytes());
    assertTrue(second.isValid());
    assertEquals("m104428,4", second.getPacketData());
  }

  @Test
  void wrappedPacketCarriesItsChecksum() {
    assertEquals("$m104428,4#00", new Packet("m104428,4").wrapped());
    assertEquals("$OK#9A", new Packet("OK").wrapped());
  }

  @Test
  void unitLengthWaitsForAWholePacket() {
    assertEquals(5, Packet.unitLength(bytes("$g#67"), 0, 5));
    assertEquals(0, Packet.unitLength(bytes("$g#6"), 0, 4), "a checksum digit still to come");
    assertEquals(0, Packet.unitLength(bytes("$m100"), 0, 5));
    assertEquals(1, Packet.unitLength(bytes("+$g#67"), 0, 6));
    assertEquals(5, Packet.unitLength(bytes("+$g#67"), 1, 6));
    assertEquals(1, Packet.unitLength(bytes("\u0003"), 0, 1));
    assertEquals(0, Packet.unitLength(bytes(""), 0, 0));
  }
}
