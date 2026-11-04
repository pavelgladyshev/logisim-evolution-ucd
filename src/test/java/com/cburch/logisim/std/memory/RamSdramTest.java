/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.instance.StdAttr;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the FPGA flow says of a RAM in the SDRAM that the controller cannot be, and its cycle. */
class RamSdramTest {

  /** The settings to change: the reasons but the HDL language, which is a preference. */
  private static List<String> settings(RamAttributes attrs) {
    return RamSdram.unsupportedReasons(attrs).stream()
        .filter(reason -> !reason.equals(Hdl.verilogOnlyReason()))
        .toList();
  }

  /** The RAM as LogOS's computer.circ has it for the FPGA. */
  private static RamAttributes computerCircsRam() {
    final var attrs = (RamAttributes) new Ram().createAttributeSet();
    attrs.setValue(RamAttributes.ATTR_FPGA_MEMORY, RamAttributes.FPGA_SDRAM);
    attrs.setValue(Mem.ENABLES_ATTR, Mem.USEBYTEENABLES);
    attrs.setValue(RamAttributes.ATTR_DBUS, RamAttributes.BUS_SEP);
    attrs.setValue(StdAttr.TRIGGER, StdAttr.TRIG_RISING);
    attrs.setValue(Mem.ASYNC_READ, true);
    attrs.setValue(Mem.ADDR_ATTR, BitWidth.create(18));
    attrs.setValue(Mem.DATA_ATTR, BitWidth.create(32));
    return attrs;
  }

  @Test
  void computerCircsRamGoesIntoTheSdram() {
    assertEquals(List.of(), settings(computerCircsRam()));
  }

  @Test
  void tooBigForTheSdram() {
    final var attrs = computerCircsRam();
    attrs.setValue(Mem.ADDR_ATTR, BitWidth.create(22));
    final var settings = settings(attrs);
    assertEquals(1, settings.size(), settings.toString());
    assertTrue(settings.get(0).contains(Mem.ADDR_ATTR.getDisplayName()), settings.get(0));
    assertTrue(settings.get(0).contains("21"), settings.get(0));
  }

  @Test
  void bidirectionalBusAndLineEnables() {
    final var attrs = computerCircsRam();
    attrs.setValue(RamAttributes.ATTR_DBUS, RamAttributes.BUS_BIDIR);
    attrs.setValue(Mem.ENABLES_ATTR, Mem.USELINEENABLES);
    assertEquals(2, settings(attrs).size(), settings(attrs).toString());
  }

  @Test
  void theSdramNeeds12FpgaClocksACycleAndBlockRamNone() {
    final var generator = new RamHdlGeneratorFactory();
    final var attrs = computerCircsRam();
    assertEquals(12, generator.getMinimumFpgaClocksPerCycle(attrs));
    attrs.setValue(RamAttributes.ATTR_FPGA_MEMORY, RamAttributes.FPGA_BLOCK_RAM);
    assertEquals(0, generator.getMinimumFpgaClocksPerCycle(attrs));
    assertEquals(List.of(), generator.getUnsupportedReasons(attrs));
  }
}
