/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.designrulecheck;

import static com.cburch.logisim.fpga.Strings.S;

import com.cburch.logisim.fpga.gui.Reporter;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Vhdl;
import com.cburch.logisim.gui.generic.OptionPane;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CorrectLabel {
  public static String getCorrectLabel(String label) {
    if (label.isEmpty()) return label;
    final var result = new StringBuilder();
    if (NUMBERS.contains(label.substring(0, 1))) result.append("L_");
    result.append(label.replace(" ", "_").replace("-", "_"));
    return result.toString();
  }

  /**
   * The HDL module (VHDL entity) name of a circuit: its corrected name, unless that names a primitive in
   * the cell library Yosys loads for an FPGA family, in which case "_logisim" is appended. A circuit called
   * ALU, DFF, ROM or MUX2 is valid HDL, but Yosys reads its Gowin, Xilinx or Lattice cell library after the
   * design, and a module of the same name stops synthesis with "Re-definition of module". The rename is
   * confined to the generated HDL: labels, annotation and file names in the circuit are untouched.
   */
  public static String getHdlModuleName(String circuitName) {
    final var name = getCorrectLabel(circuitName);
    return isVendorPrimitive(name) ? name + "_logisim" : name;
  }

  /** Whether the name is a primitive of an FPGA cell library (case-insensitively, as VHDL compares). */
  public static boolean isVendorPrimitive(String name) {
    return vendorPrimitives().contains(name.toUpperCase(Locale.ROOT));
  }

  private static Set<String> vendorPrimitiveNames;

  private static synchronized Set<String> vendorPrimitives() {
    if (vendorPrimitiveNames == null) {
      final var names = new HashSet<String>();
      try (final var in = CorrectLabel.class.getResourceAsStream("/resources/logisim/fpga/vendorPrimitives.txt")) {
        if (in != null) {
          for (final var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
            final var name = line.trim();
            if (!name.isEmpty() && !name.startsWith("#")) names.add(name.toUpperCase(Locale.ROOT));
          }
        }
      } catch (IOException e) {
        // without the list nothing is renamed, which is how it was before
      }
      vendorPrimitiveNames = names;
    }
    return vendorPrimitiveNames;
  }

  public static String hdlLabelKey(String label) {
    return hdlLabelKey(label, HdlGeneratorFactory.VHDL);
  }

  public static String hdlLabelKey(String label, String hdlType) {
    final var correctLabel = getCorrectLabel(label);
    if (HdlGeneratorFactory.VHDL.equals(hdlType)) return hdlNameKey(correctLabel);
    return correctLabel;
  }

  public static String hdlNameKey(String hdlName) {
    return hdlName.toUpperCase(Locale.ROOT);
  }

  public static boolean isCorrectLabel(String label, String errorIdentifierString) {
    final var err = nameErrors(label, errorIdentifierString);
    if (err != null) {
      Reporter.report.addFatalError(err);
      return false;
    }
    return true;
  }

  public static String vhdlNameErrors(String label) {
    return nameErrors(label, "VHDL entity name");
  }

  public static String nameErrors(String label, String errorIdentifierString) {
    if (label.isEmpty()) return null;
    for (var i = 0; i < label.length(); i++) {
      if (!CHARS.contains(label.toLowerCase().substring(i, i + 1))
          && !NUMBERS.contains(label.substring(i, i + 1))) {
        return errorIdentifierString + S.get("IllegalChar", label.substring(i, i + 1));
      }
    }
    if (Hdl.isVhdl()) {
      if (Vhdl.VHDL_KEYWORDS.contains(label.toLowerCase())) {
        return errorIdentifierString + S.get("ReservedVHDLKeyword");
      }
    } else {
      if (Hdl.isVerilog()) {
        if (VERILOG_KEYWORDS.contains(label)) {
          return errorIdentifierString + S.get("ReservedVerilogKeyword");
        }
      }
    }
    return null;
  }

  public static String hdlCorrectLabel(String label) {
    return hdlCorrectLabel(label, null);
  }

  public static String hdlCorrectLabel(String label, String hdlType) {
    if (label.isEmpty()) return null;
    if ((hdlType == null || HdlGeneratorFactory.VHDL.equals(hdlType))
        && Vhdl.VHDL_KEYWORDS.contains(label.toLowerCase())) {
      return HdlGeneratorFactory.VHDL;
    }
    if ((hdlType == null || HdlGeneratorFactory.VERILOG.equals(hdlType))
        && VERILOG_KEYWORDS.contains(label)) {
      return HdlGeneratorFactory.VERILOG;
    }
    return null;
  }

  public static String firstInvalidCharacter(String label) {
    if (label.isEmpty()) return "";
    for (var i = 0; i < label.length(); i++) {
      final var str = label.substring(i, i + 1);
      final var low = str.toLowerCase();
      if (!CHARS.contains(low) && !NUMBERS.contains(str)) {
        return low;
      }
    }
    return "";
  }

  public static boolean isCorrectLabel(String Label) {
    if (Label.isEmpty()) return true;
    for (var i = 0; i < Label.length(); i++) {
      if (!CHARS.contains(Label.toLowerCase().substring(i, i + 1))
          && !NUMBERS.contains(Label.substring(i, i + 1))) {
        return false;
      }
    }
    if (Hdl.isVhdl()) {
      return !Vhdl.VHDL_KEYWORDS.contains(Label.toLowerCase());
    } else {
      if (Hdl.isVerilog()) {
        return !VERILOG_KEYWORDS.contains(Label);
      }
    }
    return true;
  }

  public static boolean isKeyword(String label, Boolean showDialog) {
    return isKeyword(label, null, showDialog);
  }

  public static boolean isKeyword(String label, String hdlType, Boolean showDialog) {
    boolean ret = false;

    if ((hdlType == null || HdlGeneratorFactory.VHDL.equals(hdlType))
        && Vhdl.VHDL_KEYWORDS.contains(label.toLowerCase())) {
      ret = true;
      if (showDialog) OptionPane.showMessageDialog(null, S.get("VHDLKeywordNameError"));
    } else if (hdlType == null && VERILOG_KEYWORDS.contains(label.toLowerCase())) {
      if (showDialog) OptionPane.showMessageDialog(null, S.get("VerilogKeywordNameError"));
      ret = true;
    } else if (HdlGeneratorFactory.VERILOG.equals(hdlType) && VERILOG_KEYWORDS.contains(label)) {
      if (showDialog) OptionPane.showMessageDialog(null, S.get("VerilogKeywordNameError"));
      ret = true;
    }
    return ret;
  }

  private static final String[] NUMBERS_STR = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
  public static final List<String> NUMBERS = Arrays.asList(NUMBERS_STR);
  private static final String[] ALLOWED_STRINGS = {
    "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s",
    "t", "u", "v", "w", "x", "y", "z", " ", "-", "_"
  };
  private static final List<String> CHARS = Arrays.asList(ALLOWED_STRINGS);

  private static final String[] RESERVED_VERILOG_WORDS = {
    "always",
    "ifnone",
    "rpmos",
    "and",
    "initial",
    "rtran",
    "assign",
    "inout",
    "rtranif0",
    "begin",
    "input",
    "rtranif1",
    "buf",
    "integer",
    "scalared",
    "bufif0",
    "join",
    "small",
    "bufif1",
    "large",
    "specify",
    "case",
    "macromodule",
    "specparam",
    "casex",
    "medium",
    "strong0",
    "casez",
    "module",
    "strong1",
    "cmos",
    "nand",
    "supply0",
    "deassign",
    "negedge",
    "supply1",
    "default",
    "nmos",
    "table",
    "defparam",
    "nor",
    "task",
    "disable",
    "not",
    "time",
    "edge",
    "notif0",
    "tran",
    "else",
    "notif1",
    "tranif0",
    "end",
    "or",
    "tranif1",
    "endcase",
    "output",
    "tri",
    "endmodule",
    "parameter",
    "tri0",
    "endfunction",
    "pmos",
    "tri1",
    "endprimitive",
    "posedge",
    "triand",
    "endspecify",
    "primitive",
    "trior",
    "endtable",
    "pull0",
    "trireg",
    "endtask",
    "pull1",
    "vectored",
    "event",
    "pullup",
    "wait",
    "for",
    "pulldown",
    "wand",
    "force",
    "rcmos",
    "weak0",
    "forever",
    "real",
    "weak1",
    "fork",
    "realtime",
    "while",
    "function",
    "reg",
    "wire",
    "highz0",
    "release",
    "wor",
    "highz1",
    "repeat",
    "xnor",
    "if",
    "rnmos",
    "xor",
    "automatic",
    "incdir",
    "pulsestyle_ondetect",
    "cell",
    "include",
    "pulsestyle_onevent",
    "config",
    "instance",
    "signed",
    "endconfig",
    "liblist",
    "showcancelled",
    "endgenerate",
    "library",
    "unsigned",
    "generate",
    "localparam",
    "use",
    "genvar",
    "noshowcancelled"
  };

  private static final List<String> VERILOG_KEYWORDS = Arrays.asList(RESERVED_VERILOG_WORDS);
}
