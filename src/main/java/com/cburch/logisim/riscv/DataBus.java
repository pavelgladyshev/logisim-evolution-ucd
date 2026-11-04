/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv;

import static com.cburch.logisim.riscv.Strings.S;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Value;

/**
 * The data bus option of the RV32IM CPU, Block Storage and Timer.
 *
 * <p>Bidirectional, the default, reads and writes through one INOUT pin and lets the outputs float
 * while they are not in use, so that several components share one bus. Logisim simulates that, but
 * an FPGA cannot build it. Separate gives each component a data-in and a data-out pin and drives 0
 * where the bidirectional bus floats, so that a circuit chooses between components with
 * multiplexers and can go onto an FPGA.
 */
public final class DataBus {
  public static final AttributeOption BIDIRECTIONAL =
      new AttributeOption("bidir", S.getter("riscvDataBusBidir"));
  public static final AttributeOption SEPARATE =
      new AttributeOption("separate", S.getter("riscvDataBusSeparate"));
  public static final Attribute<AttributeOption> ATTR =
      Attributes.forOption(
          "dataBus", S.getter("riscvDataBusAttr"), new AttributeOption[] {BIDIRECTIONAL, SEPARATE});

  private DataBus() {}

  public static boolean isSeparate(AttributeSet attrs) {
    return SEPARATE.equals(attrs.getValue(ATTR));
  }

  /** What a separate-bus output drives for {@code value}: the value itself, or 0 if not defined. */
  public static Value known(Value value) {
    return value.isFullyDefined() ? value : Value.createKnown(value.getWidth(), 0);
  }
}
