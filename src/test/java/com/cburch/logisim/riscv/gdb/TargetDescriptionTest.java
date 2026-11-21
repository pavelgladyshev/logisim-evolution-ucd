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

import com.cburch.logisim.riscv.cpu.gdb.GdbRegisters;
import com.cburch.logisim.riscv.cpu.gdb.TargetDescription;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** The target description: well-formed, the right features and registers, and read in pieces with qXfer. */
class TargetDescriptionTest {

  private static Document parse(String xml) throws Exception {
    final var factory = DocumentBuilderFactory.newInstance();
    factory.setValidating(false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  private static Element feature(Document doc, String name) {
    final NodeList features = doc.getElementsByTagName("feature");
    for (int i = 0; i < features.getLength(); i++) {
      final var f = (Element) features.item(i);
      if (f.getAttribute("name").equals(name)) return f;
    }
    throw new AssertionError("no feature " + name);
  }

  private static List<Element> regs(Element feature) {
    final var list = new ArrayList<Element>();
    final NodeList regs = feature.getElementsByTagName("reg");
    for (int i = 0; i < regs.getLength(); i++) list.add((Element) regs.item(i));
    return list;
  }

  @Test
  void wellFormedWithArchitecture() throws Exception {
    final Document doc = parse(TargetDescription.XML);
    assertEquals("target", doc.getDocumentElement().getTagName());
    assertEquals("riscv:rv32", doc.getElementsByTagName("architecture").item(0).getTextContent());
    assertEquals(3, doc.getElementsByTagName("feature").getLength());
  }

  @Test
  void cpuFeature() throws Exception {
    final List<Element> regs = regs(feature(parse(TargetDescription.XML), "org.gnu.gdb.riscv.cpu"));
    assertEquals(33, regs.size());
    final String[] names = {"zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2", "fp", "s1", "a0", "a1", "a2", "a3",
        "a4", "a5", "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6",
        "pc"};
    for (int i = 0; i < 33; i++) {
      assertEquals(names[i], regs.get(i).getAttribute("name"));
      assertEquals(String.valueOf(i), regs.get(i).getAttribute("regnum"));
      assertEquals("32", regs.get(i).getAttribute("bitsize"));
    }
    assertEquals("code_ptr", regs.get(32).getAttribute("type"));
  }

  @Test
  void csrFeatureHasTheImplementedCsrs() throws Exception {
    final List<Element> regs = regs(feature(parse(TargetDescription.XML), "org.gnu.gdb.riscv.csr"));
    assertEquals(GdbRegisters.CSRS.size(), regs.size());
    for (Element reg : regs) {
      final Integer address = GdbRegisters.CSRS.get(reg.getAttribute("name"));
      assertTrue(address != null, reg.getAttribute("name"));
      assertEquals(String.valueOf(65 + address), reg.getAttribute("regnum"));
      assertEquals("32", reg.getAttribute("bitsize"));
    }
    assertEquals(String.valueOf(65 + 0x300), regs.stream()
        .filter(r -> r.getAttribute("name").equals("mstatus")).findFirst().orElseThrow().getAttribute("regnum"));
  }

  @Test
  void virtualFeatureHasPriv() throws Exception {
    final List<Element> regs = regs(feature(parse(TargetDescription.XML), "org.gnu.gdb.riscv.virtual"));
    assertEquals(1, regs.size());
    assertEquals("priv", regs.get(0).getAttribute("name"));
    assertEquals("4161", regs.get(0).getAttribute("regnum"));
  }

  @Test
  void readInPieces() {
    final String xml = TargetDescription.XML;
    for (int size : new int[] {1, 7, 100, 0xffb, xml.length(), xml.length() + 10}) {
      final var whole = new StringBuilder();
      int offset = 0;
      while (true) {
        final String reply = TargetDescription.xfer(
            String.format("qXfer:features:read:target.xml:%x,%x", offset, size));
        final char kind = reply.charAt(0);
        assertTrue(kind == 'm' || kind == 'l', reply);
        final String data = reply.substring(1);
        assertTrue(data.length() <= size);
        whole.append(data);
        offset += data.length();
        if (kind == 'l') break;
        assertEquals(size, data.length(), "an 'm' piece is as long as asked");
      }
      assertEquals(xml, whole.toString(), "pieces of " + size);
    }
  }

  @Test
  void edgesOfTheDocument() {
    final int len = TargetDescription.XML.length();
    assertEquals("l", TargetDescription.xfer(String.format("qXfer:features:read:target.xml:%x,10", len)));
    assertEquals("l", TargetDescription.xfer(String.format("qXfer:features:read:target.xml:%x,10", len + 5)));
    assertEquals("l" + TargetDescription.XML.substring(len - 3),
        TargetDescription.xfer(String.format("qXfer:features:read:target.xml:%x,3", len - 3)));
    assertEquals("m" + TargetDescription.XML.substring(0, 5),
        TargetDescription.xfer("qXfer:features:read:target.xml:0,5"));
  }

  @Test
  void otherRequests() {
    assertEquals("E00", TargetDescription.xfer("qXfer:features:read:other.xml:0,100"));
    assertEquals("E00", TargetDescription.xfer("qXfer:features:read:target.xml:zz,100"));
    assertEquals("E00", TargetDescription.xfer("qXfer:features:read:target.xml"));
    assertEquals("", TargetDescription.xfer("qXfer:memory-map:read::0,100"));
  }

  @Test
  void binaryEscaping() {
    assertEquals("a}\u0003b}\u0004}]}\n", TargetDescription.chunk("a#b$}*", 0, 100).substring(1));
  }
}
