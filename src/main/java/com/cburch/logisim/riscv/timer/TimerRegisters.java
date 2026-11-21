package com.cburch.logisim.riscv.timer;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;

/**
 * The Timer's two registers, the RISC-V machine timer's: mtime and mtimecmp, of 64 bits each, read and written as
 * words of 32 bits (the privileged specification's Machine Timer Registers). riscvTimer.v, the Timer on an FPGA,
 * follows the same rules, and the two are checked against each other edge for edge (support/logos/tests/timer).
 *
 * <p>The rules. Address selects one of four words, the first of these that it equals, the sums modulo 2^32: the
 * mtimecmp address (mtimecmp's low word), the mtime address (mtime's low word), the mtimecmp address plus 4
 * (mtimecmp's high word), the mtime address plus 4 (mtime's high word). At a rising clock edge, with the inputs as
 * they are before it:
 * <ol>
 *   <li>with Reset 1, mtime becomes 0 and mtimecmp MTIMECMP_RESET, and nothing else happens: no write, no count;
 *   <li>otherwise, with Write 1, the selected word takes Data In, and the other words stay;
 *   <li>and then mtime, as the write left it, counts on by one, modulo 2^64: a write of w to its low word leaves
 *       w + 1 there after the edge, and of 0xffffffff leaves 0 and carries into the high word.
 * </ol>
 * Between edges Reset 1 holds mtime at 0 and mtimecmp at MTIMECMP_RESET. The interrupt request is 1 exactly while
 * mtime >= mtimecmp, both unsigned, and Data Out is the selected word while Read is 1 and 0 otherwise: both follow
 * the registers at once, with no clock.
 */
public class TimerRegisters implements InstanceData, Cloneable {

    /**
     * mtimecmp when the simulation starts and after Reset: all ones. mtime is zero then, as the ACLINT
     * specification requires of a reset ("the MTIME register is cleared to zero"). The specifications leave
     * mtimecmp undefined after reset (the privileged specification says nothing of it, ACLINT says that the
     * MTIMECMP registers "are in unknown state"), so correct software writes both words of mtimecmp before it
     * enables the timer interrupt. All ones is this Timer's own choice: no interrupt is requested before software
     * sets mtimecmp, and software that writes only its low word, as for the one word that the Timer had up to
     * 5.0.9, gets no interrupt. TimerHdlGeneratorFactory gives riscvTimer.v this value.
     */
    public static final long MTIMECMP_RESET = 0xFFFFFFFFFFFFFFFFL;

    /** The words that an address can select, in the order in which the address is compared with theirs. */
    public enum Word { MTIMECMP_LOW, MTIME_LOW, MTIMECMP_HIGH, MTIME_HIGH, NONE }

    private static final long WORD_MASK = 0xFFFFFFFFL;

    private Value lastClock;
    private long mtime;
    private long mtimecmp;

    public TimerRegisters(Value lastClock) {
        this.lastClock = lastClock;
        reset();
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public static TimerRegisters get(InstanceState state) {
        TimerRegisters ret = (TimerRegisters) state.getData();
        if (ret == null) {
            ret = new TimerRegisters(Value.FALSE);
            state.setData(ret);
        }
        return ret;
    }

    /**
     * The word at an address of 32 bits, for the addresses of mtimecmp and mtime (their low 32 bits count): the
     * first that matches of mtimecmp's low word, mtime's low word, mtimecmp's high word and mtime's high word, so
     * that the two words the Timer had up to 5.0.9 are where they were whatever the two addresses are.
     */
    public static Word select(long address, long mtimecmpAddr, long mtimeAddr) {
        final long a = address & WORD_MASK;
        if (a == (mtimecmpAddr & WORD_MASK)) return Word.MTIMECMP_LOW;
        if (a == (mtimeAddr & WORD_MASK)) return Word.MTIME_LOW;
        if (a == ((mtimecmpAddr + 4) & WORD_MASK)) return Word.MTIMECMP_HIGH;
        if (a == ((mtimeAddr + 4) & WORD_MASK)) return Word.MTIME_HIGH;
        return Word.NONE;
    }

    /** What the Address pin selects: no word unless every bit of the address is 0 or 1. */
    public static Word select(Value address, long mtimecmpAddr, long mtimeAddr) {
        if (!address.isFullyDefined()) return Word.NONE;
        return select(address.toLongValue(), mtimecmpAddr, mtimeAddr);
    }

    /** mtime, 64 bits (unsigned). */
    public long getMtime() {
        return mtime;
    }

    public void setMtime(long value) {
        mtime = value;
    }

    /** mtimecmp, 64 bits (unsigned). */
    public long getMtimecmp() {
        return mtimecmp;
    }

    public void setMtimecmp(long value) {
        mtimecmp = value;
    }

    /** Reset: mtime 0 and mtimecmp MTIMECMP_RESET, as when the simulation starts. */
    public void reset() {
        mtime = 0;
        mtimecmp = MTIMECMP_RESET;
    }

    /** A word of the registers as 32 bits, 0 for no word. */
    public long read(Word word) {
        return switch (word) {
            case MTIMECMP_LOW -> mtimecmp & WORD_MASK;
            case MTIME_LOW -> mtime & WORD_MASK;
            case MTIMECMP_HIGH -> mtimecmp >>> 32;
            case MTIME_HIGH -> mtime >>> 32;
            case NONE -> 0;
        };
    }

    /** Puts the low 32 bits of data into a word of the registers; the other words stay. */
    public void write(Word word, long data) {
        final long d = data & WORD_MASK;
        switch (word) {
            case MTIMECMP_LOW -> mtimecmp = (mtimecmp & ~WORD_MASK) | d;
            case MTIME_LOW -> mtime = (mtime & ~WORD_MASK) | d;
            case MTIMECMP_HIGH -> mtimecmp = (mtimecmp & WORD_MASK) | (d << 32);
            case MTIME_HIGH -> mtime = (mtime & WORD_MASK) | (d << 32);
            default -> { }
        }
    }

    /**
     * A rising clock edge: with reset, the registers' reset values and nothing else; otherwise the write of data
     * to a word (NONE for no write), and then mtime counts on by one, modulo 2^64.
     */
    public void clockEdge(boolean reset, Word written, long data) {
        if (reset) {
            reset();
            return;
        }
        write(written, data);
        mtime++;
    }

    /** The interrupt request: mtime >= mtimecmp, both unsigned. */
    public boolean isInterruptRequested() {
        return Long.compareUnsigned(mtime, mtimecmp) >= 0;
    }

    public boolean updateClock(Value value) {
        Value old = lastClock;
        lastClock = value;
        return old == Value.FALSE && value == Value.TRUE;
    }
}
