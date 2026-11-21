package com.cburch.logisim.riscv.cpu.gdb;

import java.util.Map;

/**
 * The target description the GDB server gives gdb (gdb manual, "Target Descriptions"; the features are those of
 * "RISC-V Features"): org.gnu.gdb.riscv.cpu with x0-x31 and the pc, org.gnu.gdb.riscv.csr with the CSRs the CPU
 * implements and org.gnu.gdb.riscv.virtual with priv, numbered as {@link GdbRegisters} numbers them. gdb reads it
 * with qXfer:features:read:target.xml:offset,length, in pieces of at most length bytes.
 */
public final class TargetDescription {

    public static final String ANNEX = "target.xml";

    /** The document, built once: it does not change while the simulator runs. */
    public static final String XML = build();

    private static String build() {
        final var xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\"?>\n");
        xml.append("<!DOCTYPE target SYSTEM \"gdb-target.dtd\">\n");
        xml.append("<target version=\"1.0\">\n");
        xml.append("<architecture>riscv:rv32</architecture>\n");
        xml.append("<feature name=\"org.gnu.gdb.riscv.cpu\">\n");
        for (int i = 0; i < GdbRegisters.X_NAMES.length; i++) {
            final String type = switch (i) {
                case 1 -> "code_ptr";
                case 2, 3, 4, 8 -> "data_ptr";
                default -> "int";
            };
            reg(xml, GdbRegisters.X_NAMES[i], 32, type, i, null);
        }
        reg(xml, "pc", 32, "code_ptr", GdbRegisters.PC, null);
        xml.append("</feature>\n");
        xml.append("<feature name=\"org.gnu.gdb.riscv.csr\">\n");
        for (Map.Entry<String, Integer> csr : GdbRegisters.CSRS.entrySet()) {
            reg(xml, csr.getKey(), 32, "int", GdbRegisters.FIRST_CSR + csr.getValue(), "csr");
        }
        xml.append("</feature>\n");
        xml.append("<feature name=\"org.gnu.gdb.riscv.virtual\">\n");
        reg(xml, "priv", 32, "int", GdbRegisters.PRIV, null);
        xml.append("</feature>\n");
        xml.append("</target>\n");
        return xml.toString();
    }

    private static void reg(StringBuilder xml, String name, int bits, String type, int regnum, String group) {
        xml.append(String.format("<reg name=\"%s\" bitsize=\"%d\" type=\"%s\" regnum=\"%d\"", name, bits, type, regnum));
        if (group != null) xml.append(String.format(" group=\"%s\"", group));
        xml.append("/>\n");
    }

    /**
     * The reply to qXfer:object:read:annex:offset,length (gdb manual, "General Query Packets", qXfer read): for
     * features and target.xml, the length bytes from offset, after 'm' if more follow or 'l' if they are the last
     * (an empty 'l' past the end); E00 for another annex or a malformed request; empty, which means unsupported,
     * for another object.
     *
     * @param request the packet's data, starting with "qXfer:"
     */
    public static String xfer(String request) {
        final String[] parts = request.split(":", 5);
        if (parts.length != 5 || !parts[1].equals("features") || !parts[2].equals("read")) {
            return parts.length >= 3 && parts[1].equals("features") ? "E00" : "";
        }
        if (!parts[3].equals(ANNEX)) return "E00";
        final String[] range = parts[4].split(",");
        if (range.length != 2) return "E00";
        final long offset;
        final long length;
        try {
            offset = Long.parseLong(range[0], 16);
            length = Long.parseLong(range[1], 16);
        } catch (NumberFormatException e) {
            return "E00";
        }
        return chunk(XML, offset, length);
    }

    /** The piece of document from offset, of at most length bytes, as qXfer read replies with it. */
    public static String chunk(String document, long offset, long length) {
        if (offset < 0 || length < 0) return "E00";
        if (offset >= document.length()) return "l";
        final int end = (int) Math.min(document.length(), offset + length);
        return (end == document.length() ? "l" : "m") + escape(document.substring((int) offset, end));
    }

    /** Binary data in a reply: '#', '$', '}' and '*' are sent as '}' and the byte xor 0x20 (gdb manual, "Overview"). */
    static String escape(String data) {
        final var out = new StringBuilder(data.length());
        for (char c : data.toCharArray()) {
            if (c == '#' || c == '$' || c == '}' || c == '*') {
                out.append('}').append((char) (c ^ 0x20));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private TargetDescription() {}
}
