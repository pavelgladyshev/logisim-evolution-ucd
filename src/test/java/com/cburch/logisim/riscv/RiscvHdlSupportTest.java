/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.riscv.blockstorage.BlockStorage;
import com.cburch.logisim.riscv.cpu.rv32im;
import com.cburch.logisim.riscv.cpu.rv32imHdlGeneratorFactory;
import com.cburch.logisim.riscv.timer.Timer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the FPGA flow says of a riscv component that is not set up for the FPGA, and the FPGA clocks
 * that the CPU needs a cycle.
 */
class RiscvHdlSupportTest {

  private static final rv32im CPU = new rv32im();

  /** The settings to change: the reasons but the HDL language, which is a preference. */
  private static List<String> settings(ComponentFactory factory, AttributeSet attrs) {
    return factory.getHdlUnsupportedReasons(attrs).stream()
        .filter(reason -> !reason.equals(Hdl.verilogOnlyReason()))
        .toList();
  }

  /** The CPU as computer.circ has it for the FPGA. */
  private static AttributeSet fpgaCpu() {
    final var attrs = CPU.createAttributeSet();
    attrs.setValue(DataBus.ATTR, DataBus.SEPARATE);
    attrs.setValue(rv32im.ATTR_TLB_ENTRIES, rv32im.TLB_ENTRIES[0]);
    return attrs;
  }

  @Test
  void theDefaultCpuSaysWhichTwoSettingsToChange() {
    final var settings = settings(CPU, CPU.createAttributeSet());
    assertEquals(2, settings.size(), settings.toString());
    assertTrue(settings.get(0).contains(DataBus.ATTR.getDisplayName()), settings.get(0));
    assertTrue(settings.get(0).contains(DataBus.SEPARATE.toDisplayString()), settings.get(0));
    assertTrue(settings.get(1).contains(rv32im.ATTR_TLB_ENTRIES.getDisplayName()), settings.get(1));
    assertTrue(settings.get(1).contains("32"), settings.get(1));
  }

  @Test
  void computerCircsCpuHasNothingToChange() {
    assertEquals(List.of(), settings(CPU, fpgaCpu()));
  }

  @Test
  void theInstructionCacheIsToBeOff() {
    final var attrs = fpgaCpu();
    attrs.setValue(rv32im.ATTR_INSTRUCTION_CACHE, true);
    final var settings = settings(CPU, attrs);
    assertEquals(1, settings.size(), settings.toString());
    assertTrue(settings.get(0).contains(rv32im.ATTR_INSTRUCTION_CACHE.getDisplayName()));
  }

  @Test
  void supportedJustWhenThereIsNoReason() {
    final var generator = new rv32imHdlGeneratorFactory();
    for (final var attrs : List.of(CPU.createAttributeSet(), fpgaCpu())) {
      assertEquals(
          generator.getUnsupportedReasons(attrs).isEmpty(), generator.isHdlSupportedTarget(attrs));
    }
  }

  @Test
  void theCpuNeeds23FpgaClocksACycle() {
    // its inputs 8 clocks after the tick, a division whose next fetch faults, and the tick
    assertEquals(23, new rv32imHdlGeneratorFactory().getMinimumFpgaClocksPerCycle(fpgaCpu()));
  }

  @Test
  void theBlockStorageAndTheTimerNeedASeparateDataBus() {
    for (final var factory : List.of(new BlockStorage(), new Timer())) {
      final var attrs = factory.createAttributeSet();
      final var settings = settings(factory, attrs);
      assertEquals(1, settings.size(), factory.getName() + ": " + settings);
      assertTrue(settings.get(0).contains(DataBus.ATTR.getDisplayName()), settings.get(0));
      attrs.setValue(DataBus.ATTR, DataBus.SEPARATE);
      assertEquals(List.of(), settings(factory, attrs), factory.getName());
    }
  }
}
