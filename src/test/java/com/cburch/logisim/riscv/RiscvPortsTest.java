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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.riscv.blockstorage.BlockStorage;
import com.cburch.logisim.riscv.cpu.rv32im;
import com.cburch.logisim.riscv.timer.Timer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The data pins of the RV32IM CPU, Block Storage and Timer, which are separate, in and out, where
 * they were for the option of a separate data bus; and the CPU's TLB sizes.
 */
class RiscvPortsTest {

  private static final Location ORIGIN = Location.create(0, 0, false);

  private static void assertPort(InstanceFactory factory, int index, int type, int dx, int dy) {
    final var port = factory.getPorts().get(index);
    final var end = port.toEnd(ORIGIN, factory.createAttributeSet());
    final var what = factory.getName() + " port " + index;
    assertEquals(type, end.getType(), what);
    assertEquals(Location.create(dx, dy, false), end.getLocation(), what);
  }

  @Test
  void theCpuReadsOnDataInAndWritesOnDataOut() {
    final var cpu = new rv32im();
    assertEquals(15, cpu.getPorts().size());
    assertPort(cpu, rv32im.DATA_IN, EndData.INPUT_ONLY, -60, 30);
    assertPort(cpu, rv32im.DATA_OUT, EndData.OUTPUT_ONLY, 120, 30);
  }

  @Test
  void theDevicesReadOnDataInAndWriteOnDataOut() {
    final var timer = new Timer();
    assertEquals(8, timer.getPorts().size());
    assertPort(timer, Timer.DATA_IN, EndData.INPUT_ONLY, 40, 50);
    assertPort(timer, Timer.DATA_OUT, EndData.OUTPUT_ONLY, 50, 0);
    final var storage = new BlockStorage();
    assertEquals(17, storage.getPorts().size());
    assertPort(storage, BlockStorage.DATA_IN, EndData.INPUT_ONLY, -50, 60);
    assertPort(storage, BlockStorage.DATA_OUT, EndData.OUTPUT_ONLY, 130, 0);
    assertPort(storage, BlockStorage.MEM_DATA_IN, EndData.INPUT_ONLY, 130, 20);
    for (final var be :
        new int[] {BlockStorage.BE0, BlockStorage.BE1, BlockStorage.BE2, BlockStorage.BE3}) {
      assertEquals(EndData.OUTPUT_ONLY, typeOf(storage, be), "byte enable " + be);
    }
  }

  @Test
  void noPinIsShared() {
    for (final var factory : List.of(new rv32im(), new Timer(), new BlockStorage())) {
      for (var i = 0; i < factory.getPorts().size(); i++) {
        assertNotEquals(EndData.INPUT_OUTPUT, typeOf(factory, i), factory.getName() + " port " + i);
      }
      assertNull(factory.createAttributeSet().getAttribute("dataBus"), factory.getName());
    }
  }

  @Test
  void theTlbHas16Or32EntriesAnd32ByDefault() {
    final var options = List.of(rv32im.TLB_ENTRIES).stream().map(o -> o.getValue()).toList();
    assertEquals(List.of(16, 32), options);
    assertEquals(32, rv32im.tlbEntries(new rv32im().createAttributeSet()));
  }

  private static int typeOf(InstanceFactory factory, int index) {
    return factory.getPorts().get(index).toEnd(ORIGIN, factory.createAttributeSet()).getType();
  }
}
