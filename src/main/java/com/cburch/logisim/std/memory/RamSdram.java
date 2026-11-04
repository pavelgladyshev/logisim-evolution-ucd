/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.memory;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.data.ComponentMapInformationContainer;
import com.cburch.logisim.fpga.data.MappableResourcesContainer;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.instance.StdAttr;
import java.util.ArrayList;
import java.util.List;

/**
 * A RAM whose FPGA memory is SDRAM: on the FPGA it is an SDR SDRAM of 2M words of 32 bits that the board has, such
 * as the 8 MB inside the GW2AR-18 of a Sipeed Tang Nano 20K, and the board's Sdram resource gives the pins, to which
 * the RAM is mapped. Its controller, logisimSdram (resources/logisim/hdl/memory), says how it works: it does what
 * the RAM does in Logisim with an asynchronous read and a separate data bus, for a RAM of up to 21 address and 32
 * data bits, and it needs the RAM's clock cycle to last at least 12 FPGA clocks (with the Tang Nano 20K's 27 MHz,
 * Logisim's ticks go up to 4 MHz, and a clock's cycle lasts at least two of them).
 */
public final class RamSdram {

  /** The SDRAM's outputs, in the order of the board's Sdram resource and of the controller's sdramOut. */
  public static final List<String> OUTPUTS;

  /** The SDRAM's data pins. */
  public static final List<String> INOUTS;

  public static final int MAX_ADDRESS_BITS = 21;
  public static final int MAX_DATA_BITS = 32;

  /**
   * The ticks of a design with a RAM in the SDRAM start 2^START_DELAY_BITS FPGA clocks after the FPGA (at 27 MHz,
   * 155 ms): the SDRAM's start-up (200 us) and its clearing (up to 2.2 million clocks for 8 MB) are done by then.
   */
  public static final int START_DELAY_BITS = 22;

  static {
    final var outputs = new ArrayList<>(List.of("SDRAM_CLK", "SDRAM_CKE", "SDRAM_CS_N", "SDRAM_RAS_N", "SDRAM_CAS_N",
        "SDRAM_WE_N"));
    for (var i = 0; i < 4; i++) outputs.add("SDRAM_DQM" + i);
    for (var i = 0; i < 11; i++) outputs.add("SDRAM_A" + i);
    for (var i = 0; i < 2; i++) outputs.add("SDRAM_BA" + i);
    OUTPUTS = List.copyOf(outputs);
    final var inouts = new ArrayList<String>();
    for (var i = 0; i < 32; i++) inouts.add("SDRAM_DQ" + i);
    INOUTS = List.copyOf(inouts);
  }

  private RamSdram() {}

  /** Whether the RAM with these attributes goes into the SDRAM on the FPGA. */
  public static boolean isSdram(AttributeSet attrs) {
    return attrs != null
        && attrs.containsAttribute(RamAttributes.ATTR_FPGA_MEMORY)
        && RamAttributes.FPGA_SDRAM.equals(attrs.getValue(RamAttributes.ATTR_FPGA_MEMORY));
  }

  /**
   * Whether the SDRAM's controller can be the RAM with these attributes. (It also needs an FPGA clock of at most
   * 50 MHz, which is not known yet when this is asked.)
   */
  static boolean isSupported(AttributeSet attrs) {
    final var clearPin = attrs.getValue(RamAttributes.CLEAR_PIN);
    return Hdl.isVerilog()
        && Mem.USEBYTEENABLES.equals(attrs.getValue(Mem.ENABLES_ATTR))
        && RamAttributes.BUS_SEP.equals(attrs.getValue(RamAttributes.ATTR_DBUS))
        && StdAttr.TRIG_RISING.equals(attrs.getValue(StdAttr.TRIGGER))
        && Boolean.TRUE.equals(attrs.getValue(Mem.ASYNC_READ))
        && !Boolean.TRUE.equals(clearPin)
        && attrs.getValue(Mem.ADDR_ATTR).getWidth() <= MAX_ADDRESS_BITS
        && attrs.getValue(Mem.DATA_ATTR).getWidth() <= MAX_DATA_BITS;
  }

  /** The pins the RAM is mapped with. */
  static ComponentMapInformationContainer newMapInfo() {
    return new ComponentMapInformationContainer(0, OUTPUTS.size(), INOUTS.size(), null, new ArrayList<>(OUTPUTS),
        new ArrayList<>(INOUTS));
  }

  /** Whether a RAM is in the SDRAM in the design that these resources map. */
  public static boolean isUsed(MappableResourcesContainer resources) {
    if (resources == null) return false;
    for (final var map : resources.getMappableResources().values()) {
      if (map.getComponentFactory() instanceof Ram && isSdram(map.getAttributeSet())) return true;
    }
    return false;
  }
}
