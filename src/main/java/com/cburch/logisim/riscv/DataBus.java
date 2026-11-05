/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;

/**
 * The data bus of the RV32IM CPU, Block Storage and Timer: each reads its data on a Data In pin and writes it on a
 * Data Out pin, and its outputs drive 0 where a shared bus would let them float, so that a circuit chooses between
 * devices with multiplexers, as an FPGA can build it.
 */
public final class DataBus {

  private DataBus() {}

  /** What an output drives for {@code value}: the value itself, or 0 if not defined. */
  public static Value known(Value value) {
    return value.isFullyDefined() ? value : Value.createKnown(value.getWidth(), 0);
  }
}
