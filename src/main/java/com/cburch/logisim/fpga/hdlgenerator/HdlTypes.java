/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.hdlgenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.util.LineBuffer;

public class HdlTypes {

  private interface HdlType {
    /** Type definition for the current HDL, or null if the type needs none. */
    String getTypeDefinition();

    String getTypeName();

    /** Plain Verilog declaration of a signal of this type, or null to declare it as "typeName name;". */
    default String getVerilogDeclaration(String wireName) {
      return null;
    }

    /** Verilog statements that give a signal of this type its power-up value, or none. */
    default List<String> getVerilogInitialization(String wireName) {
      return List.of();
    }

    /** The power-up value of a signal of this type in VHDL, or null when it needs none. */
    default String getVhdlInitialValue() {
      return null;
    }
  }

  private static class HdlEnum implements HdlType {
    private final List<String> myEntries = new ArrayList<>();
    private final String myTypeName;

    public HdlEnum(String name) {
      myTypeName = name;
    }

    public HdlEnum add(String entry) {
      for (var item = 0; item < myEntries.size(); item++)
        if (myEntries.get(item).compareTo(entry) > 0) {
          myEntries.add(item, entry);
          return this;
        }
      myEntries.add(entry);
      return this;
    }

    @Override
    public String getTypeDefinition() {
      final var contents = new StringBuilder();
      if (Hdl.isVhdl())
        contents.append(LineBuffer.formatVhdl("{{type}} {{1}} {{is}} (", myTypeName));
      else contents.append("typedef enum { ");
      var first = true;
      for (final var entry : myEntries) {
        if (first) first = false;
        else contents.append(", ");
        contents.append(entry);
      }
      if (Hdl.isVhdl()) contents.append(");");
      else contents.append(String.format("} %s;", myTypeName));
      return contents.toString();
    }

    @Override
    public String getTypeName() {
      return myTypeName;
    }
  }

  private static class HdlArray implements HdlType {
    private final String myTypeName;
    private final String myGenericBitWidth;
    private final int myBitWidth;
    private final int myNrOfEntries;

    public HdlArray(String name, String genericBitWidth, int nrOfEntries) {
      myTypeName = name;
      myGenericBitWidth = genericBitWidth;
      myBitWidth = -1;
      myNrOfEntries = nrOfEntries;
    }

    public HdlArray(String name, int nrOfBits, int nrOfEntries) {
      myTypeName = name;
      myGenericBitWidth = null;
      myBitWidth = nrOfBits;
      myNrOfEntries = nrOfEntries;
    }

    private String msbExpression() {
      return myGenericBitWidth == null
          ? Integer.toString(myBitWidth - 1)
          : String.format("%s - 1", myGenericBitWidth);
    }

    @Override
    public String getTypeDefinition() {
      // Verilog arrays are declared directly (see getVerilogDeclaration): a typedef would need SystemVerilog.
      if (!Hdl.isVhdl()) return null;
      final var contents = new StringBuilder();
      contents.append(
          LineBuffer.formatVhdl(
              "{{type}} {{1}} {{is}} {{array}} ( {{2}} {{downto}} 0 ) {{of}} ",
              myTypeName, myNrOfEntries - 1));
      if (myGenericBitWidth == null && myBitWidth == 1) {
        contents.append("std_logic;");
      } else {
        contents
            .append("std_logic_vector( ")
            .append(msbExpression())
            .append(LineBuffer.formatVhdl(" " + "{{downto}} 0);")); // Important: The leading space is required
      }
      return contents.toString();
    }

    @Override
    public String getVerilogDeclaration(String wireName) {
      return String.format("reg [%s:0] %s [0:%d];", msbExpression(), wireName, myNrOfEntries - 1);
    }

    /** Zeros, as in a new simulation and in the FPGA. */
    @Override
    public String getVhdlInitialValue() {
      return (myGenericBitWidth == null && myBitWidth == 1) ? "(OTHERS => '0')" : "(OTHERS => (OTHERS => '0'))";
    }

    /** Zeros, as in a new simulation and in the FPGA (for a block RAM, Yosys makes these its initial contents). */
    @Override
    public List<String> getVerilogInitialization(String wireName) {
      final var index = "i_" + wireName;
      return List.of(
          String.format("integer %s;", index),
          String.format("initial for (%1$s = 0; %1$s < %2$d; %1$s = %1$s + 1) %3$s[%1$s] = 0;",
              index, myNrOfEntries, wireName));
    }

    @Override
    public String getTypeName() {
      return myTypeName;
    }
  }

  private final Map<Integer, HdlType> myTypes = new HashMap<>();
  private final Map<String, Integer> myWires = new HashMap<>();

  public HdlTypes addEnum(int identifier, String name) {
    myTypes.put(identifier, new HdlEnum(name));
    return this;
  }

  public HdlTypes addEnumEntry(int identifier, String entry) {
    if (!myTypes.containsKey(identifier))
      throw new IllegalArgumentException("Enum type not contained in array");
    final var myEnum = (HdlEnum) myTypes.get(identifier);
    myEnum.add(entry);
    return this;
  }

  public HdlTypes addArray(int identifier, String name, String genericBitWidth, int nrOfEntries) {
    myTypes.put(identifier, new HdlArray(name, genericBitWidth, nrOfEntries));
    return this;
  }

  public HdlTypes addArray(int identifier, String name, int nrOfBits, int nrOfEntries) {
    myTypes.put(identifier, new HdlArray(name, nrOfBits, nrOfEntries));
    return this;
  }

  public HdlTypes addWire(String name, int typeIdentifier) {
    myWires.put(name, typeIdentifier);
    return this;
  }

  /** Number of types that need a type definition in the current HDL. */
  public int getNrOfTypes() {
    var count = 0;
    for (final var type : myTypes.values()) if (type.getTypeDefinition() != null) count++;
    return count;
  }

  public List<String> getTypeDefinitions() {
    final var defs = LineBuffer.getHdlBuffer();
    for (final var entry : myTypes.keySet()) {
      final var definition = myTypes.get(entry).getTypeDefinition();
      if (definition != null) defs.add(definition);
    }
    return defs.getWithIndent();
  }

  /** Verilog declarations of the typed signals, sorted by name. */
  public List<String> getVerilogDeclarations() {
    final var lines = new ArrayList<String>();
    for (final var wire : new java.util.TreeSet<>(myWires.keySet())) {
      final var type = myTypes.get(myWires.get(wire));
      if (type == null) throw new IllegalArgumentException("Enum or array type not contained in array");
      final var declaration = type.getVerilogDeclaration(wire);
      lines.add(declaration != null ? declaration : String.format("%s %s;", type.getTypeName(), wire));
      lines.addAll(type.getVerilogInitialization(wire));
    }
    return lines;
  }

  /** The power-up value of a typed signal in VHDL, or null when it needs none. */
  public String getVhdlInitialValue(String wire) {
    final var type = myTypes.get(myWires.get(wire));
    return type == null ? null : type.getVhdlInitialValue();
  }

  public Map<String, String> getTypedWires() {
    final var contents = new HashMap<String, String>();
    for (final var wire : myWires.keySet()) {
      final var typeId = myWires.get(wire);
      if (!myTypes.containsKey(typeId))
        throw new IllegalArgumentException("Enum or array type not contained in array");
      contents.put(wire, myTypes.get(typeId).getTypeName());
    }
    return contents;
  }

  public void clear() {
    myTypes.clear();
    myWires.clear();
  }
}
