/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.arith;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiplierHdlGeneratorFactoryTest {

  // a is signed in the "two's complement" and "signed * unsigned" modes, b only in "two's complement" mode,
  // as in the simulation (Multiplier.propagate)

  @Test
  void unsignedNumericTypeMakesBothOperandsUnsigned() {
    final var parameters = parametersFor(Multiplier.UNSIGNED_OPTION);

    assertEquals("0", parameters.get("signedA"));
    assertEquals("0", parameters.get("signedB"));
  }

  @Test
  void signedUnsignedNumericTypeMakesOnlyTheFirstOperandSigned() {
    final var parameters = parametersFor(Multiplier.SIGNED_UNSIGNED_OPTION);

    assertEquals("1", parameters.get("signedA"));
    assertEquals("0", parameters.get("signedB"));
  }

  @Test
  void twosComplementNumericTypeMakesBothOperandsSigned() {
    final var parameters = parametersFor(Multiplier.SIGNED_OPTION);

    assertEquals("1", parameters.get("signedA"));
    assertEquals("1", parameters.get("signedB"));
  }

  private static Map<String, String> parametersFor(AttributeOption numericType) {
    final var attrs = new Multiplier().createAttributeSet();
    attrs.setValue(Multiplier.MODE_ATTR, numericType);
    return new ExposedMultiplierHdlGeneratorFactory().parameterMap(attrs);
  }

  private static class ExposedMultiplierHdlGeneratorFactory extends MultiplierHdlGeneratorFactory {
    Map<String, String> parameterMap(AttributeSet attrs) {
      return getParameterMap(attrs);
    }
  }
}
