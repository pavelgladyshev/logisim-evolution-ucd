package com.cburch.logisim.riscv.cpu.gdb;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.csrs.PRIVILEGE_MODE;
import com.cburch.logisim.riscv.cpu.rv32imData;

/**
 * gdb's m (read) and M (write) packets, through the CPU's bus with the accesses a program would make: a word (lw,
 * sw) where the address is a multiple of 4 and at least 4 bytes are left, else a halfword (lhu, sh) where it is
 * even and at least 2 are left, else a byte (lbu, sb). Each access is the CPU's own bus cycle: the address of its
 * first byte, the width (the byte enables), and for a write the data in its byte lanes. A device that decodes the
 * whole address and takes the whole word (the Timer, the PLIC, the TTY) so sees what lw and sw show it, and gdb's
 * x/xw and set {int} at a word's address read and write the device's register as a program does; x/xb at the
 * word's other bytes gives what lbu there gives (0 from such a device). The addresses are the hart's: when
 * it is in S- or U-mode and satp selects Sv32, they are virtual, and each page's physical address comes from a
 * walk of the page table through the bus (RISC-V privileged spec, "Virtual Address Translation Process", Sv32,
 * megapages included), which ignores the permission bits (R, W, X, U, A, D): the debugger sees what the hart could
 * map. The CPU's TLB and page table walker are not used and not changed. In M-mode the addresses are physical.
 *
 * <p>The reply to m is the bytes in hex; if a page is not mapped, the bytes before it, or E14 if there are none (gdb
 * takes a short reply as a partial read). The reply to M is OK, or E14 if a page is not mapped (the bytes before it
 * are written).
 *
 * <p>Each call of {@link #process} is one clock cycle: the data of the address put on the bus in the cycle before
 * comes in as dataIn.
 */
public final class MemoryRequest implements DebuggerRequest {

    private static final long PTE_V = 0x1;
    private static final long PTE_R = 0x2;
    private static final long PTE_W = 0x4;
    private static final long PTE_X = 0x8;

    private enum Phase { START, LEVEL1, LEVEL0, ACCESS }

    private final rv32imData cpu;
    private final byte[] writeData;    // null for a read
    private final long count;
    private final StringBuilder reply = new StringBuilder();

    private Phase phase = Phase.START;
    private long address;               // the next byte's (virtual) address
    private long done;                  // bytes read or written
    private int width;                  // the bytes of the access on the bus: 1, 2 or 4
    private boolean translate;
    private long rootTable;
    private long pageBase = -1;         // the virtual page whose translation is known, and its physical address
    private long pageMask;
    private long pagePhysical;

    /** A read of count bytes from address (m). */
    public static MemoryRequest read(rv32imData cpu, long address, long count) {
        return new MemoryRequest(cpu, address, count, null);
    }

    /** A write of the bytes data to address (M). */
    public static MemoryRequest write(rv32imData cpu, long address, byte[] data) {
        return new MemoryRequest(cpu, address, data.length, data);
    }

    private MemoryRequest(rv32imData cpu, long address, long count, byte[] writeData) {
        this.cpu = cpu;
        this.address = address & 0xFFFFFFFFL;
        this.count = count;
        this.writeData = writeData;
    }

    /** Whether the hart's addresses are translated now: S- or U-mode, and satp's MODE Sv32. */
    public static boolean translates(rv32imData cpu) {
        return cpu.getCurrentPrivilegeMode() != PRIVILEGE_MODE.MACHINE && cpu.getSatp().isSV32Enabled();
    }

    @Override
    public boolean process(long dataIn) {
        switch (phase) {
            case START:
                translate = translates(cpu);
                rootTable = cpu.getSatp().getRootPageTableAddress();
                break;
            case LEVEL1: {
                final long pte = dataIn & 0xFFFFFFFFL;
                if (!valid(pte)) return fail();
                if (leaf(pte)) {
                    if (((pte >> 10) & 0x3FF) != 0) return fail();   // a misaligned megapage
                    mapPage(0x3FFFFF, ((pte >> 20) & 0xFFF) << 22);
                } else {
                    busRead((((pte >> 10) & 0x3FFFFF) << 12) + ((address >> 12) & 0x3FF) * 4);
                    phase = Phase.LEVEL0;
                    return false;
                }
                break;
            }
            case LEVEL0: {
                final long pte = dataIn & 0xFFFFFFFFL;
                if (!valid(pte) || !leaf(pte)) return fail();
                mapPage(0xFFF, ((pte >> 10) & 0x3FFFFF) << 12);
                break;
            }
            case ACCESS:
                if (writeData == null) {
                    final long lanes = dataIn >> ((physical(address) & 0x3) * 8);
                    for (int i = 0; i < width; i++) {
                        reply.append(String.format("%02x", (lanes >> (8 * i)) & 0xff));
                    }
                }
                done += width;
                address = (address + width) & 0xFFFFFFFFL;
                break;
            default:
                break;
        }
        return next();
    }

    /**
     * The width of the next access: the largest of 4, 2 and 1 that the address is a multiple of and that many bytes
     * are left. It never crosses a page: pages start at multiples of 4096.
     */
    static int accessWidth(long address, long left) {
        if ((address & 3) == 0 && left >= 4) return 4;
        if ((address & 1) == 0 && left >= 2) return 2;
        return 1;
    }

    /** The next bus cycle: the end, a walk for a page not yet translated, or the next access. */
    private boolean next() {
        if (done == count) return finish(writeData == null ? reply.toString() : "OK");
        if (translate && (address & ~pageMask) != pageBase) {
            busRead(rootTable + ((address >> 22) & 0x3FF) * 4);
            phase = Phase.LEVEL1;
            return false;
        }
        width = accessWidth(address, count - done);
        // as LoadInstruction and StoreInstruction drive the bus: the address, the width, and for a store the data,
        // which getOutputData puts in the address's byte lanes for a byte or a halfword
        cpu.setAddress(Value.createKnown(32, physical(address)));
        cpu.setOutputDataWidth(width);
        if (writeData == null) {
            cpu.setOutputData(0);
            cpu.setMemRead(Value.TRUE);
            cpu.setMemWrite(Value.FALSE);
        } else {
            long value = 0;
            for (int i = 0; i < width; i++) {
                value |= (writeData[(int) done + i] & 0xffL) << (8 * i);
            }
            cpu.setOutputData(value);
            cpu.setMemRead(Value.FALSE);
            cpu.setMemWrite(Value.TRUE);
        }
        phase = Phase.ACCESS;
        return false;
    }

    private long physical(long virtual) {
        return translate ? (pagePhysical | (virtual & pageMask)) & 0xFFFFFFFFL : virtual;
    }

    private void mapPage(long mask, long physical) {
        pageMask = mask;
        pageBase = address & ~mask;
        pagePhysical = physical;
    }

    private void busRead(long physical) {
        cpu.setAddress(Value.createKnown(32, physical & 0xFFFFFFFFL));
        cpu.setOutputDataWidth(0);
        cpu.setMemRead(Value.TRUE);
        cpu.setMemWrite(Value.FALSE);
    }

    /** A PTE that is valid and not the reserved encoding W without R. */
    private static boolean valid(long pte) {
        return (pte & PTE_V) != 0 && !((pte & PTE_W) != 0 && (pte & PTE_R) == 0);
    }

    private static boolean leaf(long pte) {
        return (pte & (PTE_R | PTE_X)) != 0;
    }

    /** An address that is not mapped: the bytes read so far, or an error. */
    private boolean fail() {
        return finish(writeData == null && done > 0 ? reply.toString() : "E14");
    }

    private boolean finish(String response) {
        cpu.setOutputDataWidth(0);
        cpu.setMemRead(Value.FALSE);
        cpu.setMemWrite(Value.FALSE);
        cpu.addDebuggerResponse(response);
        return true;
    }
}
