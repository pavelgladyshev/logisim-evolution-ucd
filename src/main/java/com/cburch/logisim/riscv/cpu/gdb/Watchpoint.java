package com.cburch.logisim.riscv.cpu.gdb;

import java.util.Objects;

/**
 * A watchpoint of gdb's (packets Z2, Z3 and Z4, gdb manual "Packets"): a range of the hart's (virtual) addresses,
 * and whether stores (watch), loads (rwatch) or both (awatch) to it stop the CPU. The CPU stops after the load or
 * store, and the stop reply names the kind and an address in the range (T05watch:addr;), from which gdb tells which
 * watchpoint fired.
 */
public final class Watchpoint {

    public enum Kind {
        WRITE("watch"), READ("rwatch"), ACCESS("awatch");

        private final String stopName;

        Kind(String stopName) {
            this.stopName = stopName;
        }

        /** The kind for the type of a Z or z packet (2, 3 or 4), or null. */
        public static Kind ofPacketType(int type) {
            return switch (type) {
                case 2 -> WRITE;
                case 3 -> READ;
                case 4 -> ACCESS;
                default -> null;
            };
        }
    }

    private final Kind kind;
    private final long address;
    private final long length;

    public Watchpoint(Kind kind, long address, long length) {
        this.kind = kind;
        this.address = address & 0xFFFFFFFFL;
        this.length = Math.max(1, length);
    }

    /** Whether a load (store false) or store (true) of width bytes at the virtual address va touches the range. */
    public boolean hits(long va, int width, boolean store) {
        if (store ? kind == Kind.READ : kind == Kind.WRITE) return false;
        final long start = va & 0xFFFFFFFFL;
        return start < address + length && address < start + width;
    }

    /** The stop reply's part for an access at va that hit: kind:address; with an address inside the range. */
    public String stopReason(long va) {
        return String.format("%s:%x;", kind.stopName, Math.max(va & 0xFFFFFFFFL, address));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Watchpoint w && w.kind == kind && w.address == address && w.length == length;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, address, length);
    }
}
