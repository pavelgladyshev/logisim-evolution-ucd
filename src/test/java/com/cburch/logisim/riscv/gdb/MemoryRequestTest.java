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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.GDBServer;
import com.cburch.logisim.riscv.cpu.csrs.PRIVILEGE_MODE;
import com.cburch.logisim.riscv.cpu.gdb.MemoryRequest;
import com.cburch.logisim.riscv.cpu.rv32imData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** gdb's m and M: physical addresses in M-mode, Sv32 virtual ones (pages and megapages) in S- and U-mode. */
class MemoryRequestTest {

  /** Takes the replies that the CPU would hand the server's thread. */
  private static final class Replies extends GDBServer {
    String last;

    @Override
    public void setDebuggerResponse(String response) {
      last = response;
    }
  }

  private final Map<Long, Long> words = new HashMap<>();
  private final Replies replies = new Replies();
  private rv32imData cpu;
  private int busReads;
  private int busWrites;
  private final List<Integer> widths = new ArrayList<>();
  /** A device register at DEVICE, as the Timer has one: it answers only its exact address, and takes whole words. */
  private static final long DEVICE = 0x02004000L;
  private long device;

  private long word(long address) {
    return words.getOrDefault(address & ~3L, 0L);
  }

  private void putBytes(long address, String s) {
    for (int i = 0; i < s.length(); i++) {
      final long a = address + i;
      final int shift = (int) (a & 3) * 8;
      words.put(a & ~3L, (word(a) & ~(0xffL << shift)) | ((long) s.charAt(i) << shift));
    }
  }

  /** Runs a request cycle by cycle, the memory answering (or writing) what the CPU put on the bus. */
  private String run(MemoryRequest request) {
    long data = 0;
    for (int i = 0; i < 10000; i++) {
      if (request.process(data)) return replies.last;
      final long address = cpu.getAddress().toLongValue();
      final boolean isDevice = (address & ~3L) == DEVICE;
      if (cpu.getMemWrite() == Value.TRUE) {
        // a RAM with byte enables: the lanes of the access's width, from the data bus as the CPU drives it
        busWrites++;
        widths.add(cpu.getOutputDataWidth());
        assertTrue(cpu.getMemRead() == Value.FALSE, "a store's bus cycle: Mem Read off");
        final long bus = cpu.getOutputData().toLongValue();
        if (isDevice) {
          if (address == DEVICE) device = bus & 0xFFFFFFFFL;
        } else {
          final int first = (int) (address & 3);
          long w = word(address);
          for (int lane = first; lane < first + cpu.getOutputDataWidth(); lane++) {
            w = (w & ~(0xffL << (8 * lane))) | (bus & (0xffL << (8 * lane)));
          }
          words.put(address & ~3L, w);
        }
      } else if (cpu.getMemRead() == Value.TRUE) {
        busReads++;
        widths.add(cpu.getOutputDataWidth());
      }
      data = isDevice ? (address == DEVICE ? device : 0) : word(address);
    }
    throw new AssertionError("the request did not finish");
  }

  @BeforeEach
  void setup() {
    cpu = new rv32imData(Value.FALSE, 0, 1234, false, false, rv32imData.CPUState.STOPPED, null);
    cpu.setGDBServer(replies);
    // Sv32: the root table at 0x1000. 0x00400000 (VPN[1] 1) points to the table at 0x2000, which maps
    // 0x00400000 to 0x10000 and 0x00401000 to 0x23000; 0x80000000 (VPN[1] 0x200) is a megapage at 0x00c00000;
    // 0x00800000 (VPN[1] 2) is not valid; 0x00c00000 (VPN[1] 3) is a misaligned megapage. No permission bits but V
    // on the leaves except R: gdb's accesses ignore them.
    words.put(0x1000L + 1 * 4, (2L << 10) | 0x1);
    words.put(0x1000L + 0x200 * 4, (0x3L << 20) | 0x3);
    words.put(0x1000L + 3 * 4, (0x3L << 20) | (1L << 10) | 0x3);
    words.put(0x2000L + 0 * 4, (0x10L << 10) | 0x3);
    words.put(0x2000L + 1 * 4, (0x23L << 10) | 0x3);
    putBytes(0x10ffeL, "hello");      // crosses into the next virtual page, 0x23000
    putBytes(0x23000L - 2, "xx");     // what an untranslated read of 0x10ffe's neighbours would not show
    putBytes(0x23000L, "llo");
    putBytes(0x00c00010L, "mega");
    cpu.setCSR(0x180, 0x80000001L);
  }

  private static String hex(String s) {
    final var out = new StringBuilder();
    for (char c : s.toCharArray()) out.append(String.format("%02x", (int) c));
    return out.toString();
  }

  @Test
  void machineModeIsPhysical() {
    assertEquals(PRIVILEGE_MODE.MACHINE, cpu.getCurrentPrivilegeMode());
    assertEquals(hex("hel"), run(MemoryRequest.read(cpu, 0x10ffe, 3)));
  }

  @Test
  void supervisorModeTranslatesAcrossPages() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    assertTrue(MemoryRequest.translates(cpu));
    assertEquals(hex("hello"), run(MemoryRequest.read(cpu, 0x00400ffe, 5)));
    assertEquals(4 + 3, busReads, "two walks of two reads each, and halfwords at 0xffe and 0x1000, a byte at 0x1002");
  }

  @Test
  void userModeAndMegapage() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.USER);
    assertEquals(hex("mega"), run(MemoryRequest.read(cpu, 0x80000010L, 4)));
  }

  @Test
  void bareSatpIsNotTranslated() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    cpu.setCSR(0x180, 0x1);
    assertEquals(hex("hel"), run(MemoryRequest.read(cpu, 0x10ffe, 3)));
  }

  @Test
  void unmappedIsAnError() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    assertEquals("E14", run(MemoryRequest.read(cpu, 0x00800000, 4)));
    assertEquals("E14", run(MemoryRequest.read(cpu, 0x00402000, 4)));   // a level-0 entry that is not valid
    assertEquals("E14", run(MemoryRequest.read(cpu, 0x00c00000, 4)));   // a misaligned megapage
    assertEquals("E14", run(MemoryRequest.write(cpu, 0x00800000, new byte[] {1})));
  }

  @Test
  void partialReadUpToAnUnmappedPage() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    words.put(0x2000L + 1 * 4, 0L);   // 0x00401000 not mapped now
    assertEquals(hex("he"), run(MemoryRequest.read(cpu, 0x00400ffe, 5)));
  }

  @Test
  void writeAtAVirtualAddress() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    assertEquals("OK", run(MemoryRequest.write(cpu, 0x00400fff, new byte[] {'E', 'L', 'L'})));
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
    assertEquals(hex("hE"), run(MemoryRequest.read(cpu, 0x10ffe, 2)));
    assertEquals(hex("LLo"), run(MemoryRequest.read(cpu, 0x23000, 3)));
  }

  @Test
  void cpuTranslationStateIsNotTouched() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    run(MemoryRequest.read(cpu, 0x00400ffe, 5));
    assertTrue(cpu.getPtw().isIdle());
    cpu.getTlb().translate(0x00400ffe, cpu.getCurrentASID());
    assertTrue(!cpu.getTlb().resultHit, "the TLB has no entry from the debugger's walk");
  }

  @Test
  void emptyRead() {
    assertEquals("", run(MemoryRequest.read(cpu, 0x100, 0)));
  }

  /** The widths of the data accesses only (the walks' reads have width 0). */
  private List<Integer> dataWidths() {
    return widths.stream().filter(w -> w != 0).toList();
  }

  @Test
  void alignedWordsTakeOneCycleEach() {
    putBytes(0x200, "abcdefghijklmnop");
    assertEquals(hex("abcdefghijklmnop"), run(MemoryRequest.read(cpu, 0x200, 16)));
    assertEquals(List.of(4, 4, 4, 4), dataWidths());
    assertEquals(4, busReads);
  }

  @Test
  void unalignedHeadAndTail() {
    putBytes(0x200, "abcdefghijklmnop");
    assertEquals(hex("bcdefghijk"), run(MemoryRequest.read(cpu, 0x201, 10)));
    // b (byte), cd (halfword), efgh ijk... : 0x201 1, 0x202 2, 0x204 4, 0x208 2, 0x20a 1
    assertEquals(List.of(1, 2, 4, 2, 1), dataWidths());
  }

  @Test
  void oneToSevenBytes() {
    putBytes(0x300, "ABCDEFGH");
    final int[][] expected = {{1}, {2}, {2, 1}, {4}, {4, 1}, {4, 2}, {4, 2, 1}};
    for (int n = 1; n <= 7; n++) {
      widths.clear();
      assertEquals(hex("ABCDEFGH".substring(0, n)), run(MemoryRequest.read(cpu, 0x300, n)), n + " bytes");
      assertEquals(Arrays.stream(expected[n - 1]).boxed().toList(), dataWidths(), n + " bytes");
    }
  }

  @Test
  void deviceWordAsLwAndSwSeeIt() {
    device = 0x12345678L;
    assertEquals("78563412", run(MemoryRequest.read(cpu, DEVICE, 4)), "x/xw: lw");
    assertEquals("78", run(MemoryRequest.read(cpu, DEVICE, 1)), "x/xb at the word: lbu, the device's low byte");
    assertEquals("000000", run(MemoryRequest.read(cpu, DEVICE + 1, 3)), "its other bytes: 0, as lbu there gives");
    assertEquals("OK", run(MemoryRequest.write(cpu, DEVICE, new byte[] {(byte) 0xef, (byte) 0xcd, (byte) 0xab,
        (byte) 0x89})));
    assertEquals(0x89abcdefL, device, "set {int}: sw, the whole word");
    assertEquals("efcdab89", run(MemoryRequest.read(cpu, DEVICE, 4)));
  }

  @Test
  void writesUseTheStoresWidths() {
    putBytes(0x400, "........");
    assertEquals("OK", run(MemoryRequest.write(cpu, 0x401, "ABCDEF".getBytes(StandardCharsets.ISO_8859_1))));
    assertEquals(List.of(1, 2, 2, 1), dataWidths());
    assertEquals(hex(".ABCDEF."), run(MemoryRequest.read(cpu, 0x400, 8)));
    widths.clear();
    busWrites = 0;
    assertEquals("OK", run(MemoryRequest.write(cpu, 0x400, "abcdefgh".getBytes(StandardCharsets.ISO_8859_1))));
    assertEquals(List.of(4, 4), dataWidths());
    assertEquals(2, busWrites, "two words: two bus cycles");
    assertEquals(hex("abcdefgh"), run(MemoryRequest.read(cpu, 0x400, 8)));
  }

  @Test
  void wordsAcrossAPageBoundaryWithTranslation() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    putBytes(0x10ff8L, "12345678");
    putBytes(0x23000L, "9abcdefg");
    assertEquals(hex("123456789abcdefg"), run(MemoryRequest.read(cpu, 0x00400ff8, 16)));
    assertEquals(List.of(4, 4, 4, 4), dataWidths());
    assertEquals(4 + 4, busReads, "two walks of two reads each, and four words");
    assertEquals("OK", run(MemoryRequest.write(cpu, 0x00400ffc, "WXYZwxyz".getBytes(StandardCharsets.ISO_8859_1))));
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
    assertEquals(hex("WXYZ"), run(MemoryRequest.read(cpu, 0x10ffc, 4)));
    assertEquals(hex("wxyz"), run(MemoryRequest.read(cpu, 0x23000, 4)));
  }

  @Test
  void wordsStopAtAnUnmappedPage() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    words.put(0x2000L + 1 * 4, 0L);   // 0x00401000 not mapped now
    putBytes(0x10ff8L, "12345678");
    assertEquals(hex("12345678"), run(MemoryRequest.read(cpu, 0x00400ff8, 16)));
    assertEquals("E14", run(MemoryRequest.write(cpu, 0x00400ffc, "WXYZwxyz".getBytes(StandardCharsets.ISO_8859_1))));
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
    assertEquals(hex("WXYZ"), run(MemoryRequest.read(cpu, 0x10ffc, 4)), "the bytes before the page are written");
  }
}
