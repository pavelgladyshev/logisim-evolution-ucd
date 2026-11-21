/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.timer.TimerRegisters.Word;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The rules of the Timer's registers, the RISC-V machine timer's mtime and mtimecmp of 64 bits: the count and its
 * carry into the high word, the unsigned comparison across 2^32 and 2^63, writes to each half, Reset, the words
 * that an address selects, and the three stores with which RV32 software sets mtimecmp.
 */
class TimerRegistersTest {

    private static final long MTIMECMP = Timer.RISCV_MTIMECMP_ADDR_DEFAULT;
    private static final long MTIME = Timer.RISCV_MTIME_ADDR_DEFAULT;

    private static TimerRegisters registers(long mtime, long mtimecmp) {
        final var regs = new TimerRegisters(Value.FALSE);
        regs.setMtime(mtime);
        regs.setMtimecmp(mtimecmp);
        return regs;
    }

    /** A rising edge with no reset and no write. */
    private static void tick(TimerRegisters regs) {
        regs.clockEdge(false, Word.NONE, 0);
    }

    /** A rising edge at which software stores a word. */
    private static void store(TimerRegisters regs, Word word, long data) {
        regs.clockEdge(false, word, data);
    }

    // ------------------------------------------------------------------------------------------ reset

    @Test
    void atTheStartMtimeIs0AndMtimecmpItsResetValueAndNoInterruptIsRequested() {
        final var regs = new TimerRegisters(Value.FALSE);
        assertEquals(0, regs.getMtime());
        assertEquals(TimerRegisters.MTIMECMP_RESET, regs.getMtimecmp());
        assertFalse(regs.isInterruptRequested());
    }

    @Test
    void theResetValueOfMtimecmpIsAllOnes() {
        // the largest value: mtime is below it until it is 2^64 - 1 itself
        assertEquals(0xFFFFFFFFFFFFFFFFL, TimerRegisters.MTIMECMP_RESET);
        final var regs = new TimerRegisters(Value.FALSE);
        assertEquals(0xFFFFFFFFL, regs.read(Word.MTIMECMP_LOW));
        assertEquals(0xFFFFFFFFL, regs.read(Word.MTIMECMP_HIGH));
        regs.setMtime(0xFFFFFFFFFFFFFFFEL);
        assertFalse(regs.isInterruptRequested());
    }

    @Test
    void resetPutsBothRegistersBack() {
        final var regs = registers(0x123456789ABCDEFL, 5);
        assertTrue(regs.isInterruptRequested());
        regs.reset();
        assertEquals(0, regs.getMtime());
        assertEquals(TimerRegisters.MTIMECMP_RESET, regs.getMtimecmp());
        assertFalse(regs.isInterruptRequested());
    }

    @Test
    void anEdgeWithResetDoesNothingButReset() {
        for (final var word : Word.values()) {
            final var regs = registers(0x1111111122222222L, 0x3333333344444444L);
            regs.clockEdge(true, word, 0x55555555L);
            assertEquals(0, regs.getMtime(), word + ": no count, no write");
            assertEquals(TimerRegisters.MTIMECMP_RESET, regs.getMtimecmp(), word.toString());
        }
    }

    // ------------------------------------------------------------------------------------------ the count

    @Test
    void mtimeCountsOnByOneAtEachEdge() {
        final var regs = new TimerRegisters(Value.FALSE);
        for (var i = 1; i <= 1000; i++) {
            tick(regs);
            assertEquals(i, regs.getMtime());
        }
        assertEquals(TimerRegisters.MTIMECMP_RESET, regs.getMtimecmp());
    }

    @Test
    void theCountCarriesFromTheLowWordIntoTheHighWord() {
        final var regs = registers(0x00000000FFFFFFFEL, -1L);
        tick(regs);
        assertEquals(0x00000000FFFFFFFFL, regs.getMtime());
        assertEquals(0xFFFFFFFFL, regs.read(Word.MTIME_LOW));
        assertEquals(0, regs.read(Word.MTIME_HIGH));
        tick(regs);
        assertEquals(0x0000000100000000L, regs.getMtime());
        assertEquals(0, regs.read(Word.MTIME_LOW));
        assertEquals(1, regs.read(Word.MTIME_HIGH));
        tick(regs);
        assertEquals(0x0000000100000001L, regs.getMtime());
    }

    @Test
    void theCountGoesThrough2To31And2To63AsUnsigned() {
        // the Timer of 5.0.9 counted 31 bits, and went from 0x7fffffff back to 0
        var regs = registers(0x000000007FFFFFFFL, -1L);
        tick(regs);
        assertEquals(0x0000000080000000L, regs.getMtime());
        regs = registers(0x7FFFFFFFFFFFFFFFL, -1L);
        tick(regs);
        assertEquals(0x8000000000000000L, regs.getMtime());
        assertEquals(0x80000000L, regs.read(Word.MTIME_HIGH));
    }

    @Test
    void theCountWrapsAt2To64() {
        final var regs = registers(0xFFFFFFFFFFFFFFFEL, 10);
        tick(regs);
        assertEquals(0xFFFFFFFFFFFFFFFFL, regs.getMtime());
        assertTrue(regs.isInterruptRequested());
        tick(regs);
        assertEquals(0, regs.getMtime());
        assertFalse(regs.isInterruptRequested(), "0 is below mtimecmp again");
    }

    // ------------------------------------------------------------------------------------------ the comparison

    @Test
    void theInterruptIsRequestedWhileMtimeIsAtLeastMtimecmp() {
        assertFalse(registers(4, 5).isInterruptRequested());
        assertTrue(registers(5, 5).isInterruptRequested());
        assertTrue(registers(6, 5).isInterruptRequested());
        assertTrue(registers(0, 0).isInterruptRequested());
        assertFalse(registers(0, 1).isInterruptRequested());
    }

    @Test
    void theComparisonIsOf64BitsAcross2To32() {
        assertTrue(registers(0x0000000100000000L, 0x00000000FFFFFFFFL).isInterruptRequested());
        assertFalse(registers(0x00000000FFFFFFFFL, 0x0000000100000000L).isInterruptRequested());
        // the low words alone would say the opposite
        assertFalse(registers(0x00000001FFFFFFFFL, 0x0000000200000000L).isInterruptRequested());
        assertTrue(registers(0x0000000200000000L, 0x00000001FFFFFFFFL).isInterruptRequested());
        // and so would the high words alone
        assertTrue(registers(0x0000000500000007L, 0x0000000500000007L).isInterruptRequested());
        assertFalse(registers(0x0000000500000006L, 0x0000000500000007L).isInterruptRequested());
    }

    @Test
    void theComparisonIsUnsigned() {
        final long top = 0x8000000000000000L;   // negative as a Java long
        assertTrue(registers(top, top - 1).isInterruptRequested());
        assertFalse(registers(top - 1, top).isInterruptRequested());
        assertTrue(registers(-1L, 0).isInterruptRequested());
        assertFalse(registers(0, -1L).isInterruptRequested());
        assertTrue(registers(-1L, -1L).isInterruptRequested());
        assertFalse(registers(-2L, -1L).isInterruptRequested());
        // 32 bits: 0x80000000 and 0xffffffff in the low word are not negative either
        assertTrue(registers(0x80000000L, 0x7FFFFFFFL).isInterruptRequested());
        assertFalse(registers(0x7FFFFFFFL, 0xFFFFFFFFL).isInterruptRequested());
    }

    @Test
    void theInterruptComesWhenMtimeReachesAnMtimecmpBeyond2To32() {
        final long start = 0x00000000FFFFFFF0L;
        final long alarm = start + 0x20;         // 0x1_00000010
        final var regs = registers(start, alarm);
        for (var time = start; time != alarm; time++) {
            assertEquals(time, regs.getMtime());
            assertFalse(regs.isInterruptRequested(), Long.toHexString(time));
            tick(regs);
        }
        assertEquals(alarm, regs.getMtime());
        assertTrue(regs.isInterruptRequested());
        tick(regs);
        assertTrue(regs.isInterruptRequested(), "and stays until mtimecmp is set again");
    }

    @Test
    void theInterruptComesWhenMtimeReachesAnMtimecmpJustBelow2To64() {
        final long start = 0xFFFFFFFFFFFFFFE0L;
        final long alarm = 0xFFFFFFFFFFFFFFF0L;
        final var regs = registers(start, alarm);
        for (var time = start; time != alarm; time++) {
            assertFalse(regs.isInterruptRequested(), Long.toHexString(time));
            tick(regs);
        }
        for (var time = alarm; time != 0; time++) {
            assertEquals(time, regs.getMtime());
            assertTrue(regs.isInterruptRequested(), Long.toHexString(time));
            tick(regs);
        }
        assertEquals(0, regs.getMtime());
        assertFalse(regs.isInterruptRequested(), "mtime wrapped to 0");
    }

    // ------------------------------------------------------------------------------------------ reads and writes

    @Test
    void eachWordIsReadByItself() {
        final var regs = registers(0x1111111122222222L, 0x3333333344444444L);
        assertEquals(0x22222222L, regs.read(Word.MTIME_LOW));
        assertEquals(0x11111111L, regs.read(Word.MTIME_HIGH));
        assertEquals(0x44444444L, regs.read(Word.MTIMECMP_LOW));
        assertEquals(0x33333333L, regs.read(Word.MTIMECMP_HIGH));
        assertEquals(0, regs.read(Word.NONE));
        final var top = registers(0x80000000FFFFFFFFL, 0xFFFFFFFF80000000L);
        assertEquals(0xFFFFFFFFL, top.read(Word.MTIME_LOW));
        assertEquals(0x80000000L, top.read(Word.MTIME_HIGH));
        assertEquals(0x80000000L, top.read(Word.MTIMECMP_LOW));
        assertEquals(0xFFFFFFFFL, top.read(Word.MTIMECMP_HIGH));
    }

    @Test
    void writesChangeTheirWordOnly() {
        final long mtime = 0x1111111122222222L;
        final long mtimecmp = 0x3333333344444444L;
        var regs = registers(mtime, mtimecmp);
        regs.write(Word.MTIMECMP_LOW, 0xAAAAAAAAL);
        assertEquals(0x33333333AAAAAAAAL, regs.getMtimecmp());
        assertEquals(mtime, regs.getMtime());
        regs = registers(mtime, mtimecmp);
        regs.write(Word.MTIMECMP_HIGH, 0xAAAAAAAAL);
        assertEquals(0xAAAAAAAA44444444L, regs.getMtimecmp());
        assertEquals(mtime, regs.getMtime());
        regs = registers(mtime, mtimecmp);
        regs.write(Word.MTIME_LOW, 0xAAAAAAAAL);
        assertEquals(0x11111111AAAAAAAAL, regs.getMtime());
        assertEquals(mtimecmp, regs.getMtimecmp());
        regs = registers(mtime, mtimecmp);
        regs.write(Word.MTIME_HIGH, 0xAAAAAAAAL);
        assertEquals(0xAAAAAAAA22222222L, regs.getMtime());
        assertEquals(mtimecmp, regs.getMtimecmp());
        regs = registers(mtime, mtimecmp);
        regs.write(Word.NONE, 0xAAAAAAAAL);
        assertEquals(mtime, regs.getMtime());
        assertEquals(mtimecmp, regs.getMtimecmp());
    }

    @Test
    void writesTake32BitsOfTheirData() {
        final var regs = registers(0, 0);
        regs.write(Word.MTIMECMP_LOW, -1L);                    // as an undefined Data In gives
        assertEquals(0x00000000FFFFFFFFL, regs.getMtimecmp());
        regs.write(Word.MTIMECMP_HIGH, 0x1234567880000001L);
        assertEquals(0x80000001FFFFFFFFL, regs.getMtimecmp());
        regs.write(Word.MTIME_LOW, 0xFFFFFFFF00000007L);
        assertEquals(7, regs.getMtime());
    }

    @Test
    void storesToMtimecmpAtAnEdgeLeaveTheCountAlone() {
        final var regs = registers(100, 0x3333333344444444L);
        store(regs, Word.MTIMECMP_LOW, 0x12345678L);
        assertEquals(0x3333333312345678L, regs.getMtimecmp());
        assertEquals(101, regs.getMtime());
        store(regs, Word.MTIMECMP_HIGH, 0x9ABCDEF0L);
        assertEquals(0x9ABCDEF012345678L, regs.getMtimecmp());
        assertEquals(102, regs.getMtime());
    }

    @Test
    void storesToMtimeAreFollowedByTheCountAtTheSameEdge() {
        final var regs = registers(0x1111111122222222L, -1L);
        store(regs, Word.MTIME_LOW, 0x00000050L);
        assertEquals(0x1111111100000051L, regs.getMtime());
        store(regs, Word.MTIME_HIGH, 0x00000007L);
        assertEquals(0x0000000700000052L, regs.getMtime());
    }

    @Test
    void allOnesStoredToMtimesLowWordCarryIntoItsHighWordAtTheSameEdge() {
        final var regs = registers(0x0000000500000000L, -1L);
        store(regs, Word.MTIME_LOW, 0xFFFFFFFFL);
        assertEquals(0x0000000600000000L, regs.getMtime());
        // and a store to the high word takes the carry of the low word as it is
        final var carry = registers(0x00000005FFFFFFFFL, -1L);
        store(carry, Word.MTIME_HIGH, 0x00000009L);
        assertEquals(0x0000000A00000000L, carry.getMtime());
    }

    @Test
    void mtimeCanBeSetJustBelow2To32AndJustBelow2To64() {
        // low word first, with room for the edges in between; then the high word
        final var regs = new TimerRegisters(Value.FALSE);
        store(regs, Word.MTIME_LOW, 0xFFFFFFF0L);
        store(regs, Word.MTIME_HIGH, 0);
        assertEquals(0x00000000FFFFFFF2L, regs.getMtime());
        store(regs, Word.MTIME_HIGH, 0xFFFFFFFFL);
        assertEquals(0xFFFFFFFFFFFFFFF3L, regs.getMtime());
        for (var i = 0; i < 0xD; i++) tick(regs);
        assertEquals(0, regs.getMtime());
    }

    // ------------------------------------------------------------------------------------------ the addresses

    @Test
    void theHighWordsAre4AboveTheLowWords() {
        assertEquals(Word.MTIMECMP_LOW, TimerRegisters.select(0x02004000L, MTIMECMP, MTIME));
        assertEquals(Word.MTIMECMP_HIGH, TimerRegisters.select(0x02004004L, MTIMECMP, MTIME));
        assertEquals(Word.MTIME_LOW, TimerRegisters.select(0x0200BFF8L, MTIMECMP, MTIME));
        assertEquals(Word.MTIME_HIGH, TimerRegisters.select(0x0200BFFCL, MTIMECMP, MTIME));
    }

    @Test
    void noOtherAddressSelectsAWord() {
        for (final long address : new long[] {0, 0x02003FFCL, 0x02004001L, 0x02004002L, 0x02004003L, 0x02004005L,
            0x02004008L, 0x0200BFF4L, 0x0200BFF9L, 0x0200BFFBL, 0x0200BFFDL, 0x0200C000L, 0x82004000L,
            0xFFFFFFFFL}) {
            assertEquals(Word.NONE, TimerRegisters.select(address, MTIMECMP, MTIME), Long.toHexString(address));
        }
    }

    @Test
    void anAddressThatIsNotFullyDefinedSelectsNoWord() {
        assertEquals(Word.MTIMECMP_LOW, TimerRegisters.select(Value.createKnown(32, MTIMECMP), MTIMECMP, MTIME));
        assertEquals(Word.NONE, TimerRegisters.select(Value.createUnknown(BitWidth.create(32)), MTIMECMP, MTIME));
        assertEquals(Word.NONE, TimerRegisters.select(Value.createError(BitWidth.create(32)), MTIMECMP, MTIME));
        // nor at 0xffffffff, which an undefined address is as a number
        assertEquals(Word.NONE, TimerRegisters.select(Value.createUnknown(BitWidth.create(32)), 0xFFFFFFFFL, MTIME));
        assertEquals(Word.MTIMECMP_LOW, TimerRegisters.select(Value.createKnown(32, 0xFFFFFFFFL), 0xFFFFFFFFL, MTIME));
    }

    @Test
    void theHighWordsAddressesWrapAt2To32() {
        assertEquals(Word.MTIMECMP_LOW, TimerRegisters.select(0xFFFFFFFCL, 0xFFFFFFFCL, 0xFFFFFFFFL));
        assertEquals(Word.MTIMECMP_HIGH, TimerRegisters.select(0x00000000L, 0xFFFFFFFCL, 0xFFFFFFFFL));
        assertEquals(Word.MTIME_LOW, TimerRegisters.select(0xFFFFFFFFL, 0xFFFFFFFCL, 0xFFFFFFFFL));
        assertEquals(Word.MTIME_HIGH, TimerRegisters.select(0x00000003L, 0xFFFFFFFCL, 0xFFFFFFFFL));
    }

    @Test
    void onlyTheLow32BitsOfTheAddressesCount() {
        assertEquals(Word.MTIMECMP_LOW, TimerRegisters.select(0x02004000L, 0x7_02004000L, MTIME));
        assertEquals(Word.MTIME_HIGH, TimerRegisters.select(0x0200BFFCL, MTIMECMP, 0xFFFFFFFF_0200BFF8L));
        assertEquals(Word.MTIME_LOW, TimerRegisters.select(0xFFFFFFFF_0200BFF8L, MTIMECMP, MTIME));
    }

    @Test
    void theWordsOf509WinWhereAddressesMeet() {
        // the same address for both: mtimecmp, as before
        assertEquals(Word.MTIMECMP_LOW, TimerRegisters.select(0x1000, 0x1000, 0x1000));
        assertEquals(Word.MTIMECMP_HIGH, TimerRegisters.select(0x1004, 0x1000, 0x1000));
        // mtime in the word after mtimecmp: it stays there, and hides mtimecmp's high word
        assertEquals(Word.MTIMECMP_LOW, TimerRegisters.select(0x1000, 0x1000, 0x1004));
        assertEquals(Word.MTIME_LOW, TimerRegisters.select(0x1004, 0x1000, 0x1004));
        assertEquals(Word.MTIME_HIGH, TimerRegisters.select(0x1008, 0x1000, 0x1004));
        // mtimecmp in the word after mtime
        assertEquals(Word.MTIME_LOW, TimerRegisters.select(0x1000, 0x1004, 0x1000));
        assertEquals(Word.MTIMECMP_LOW, TimerRegisters.select(0x1004, 0x1004, 0x1000));
        assertEquals(Word.MTIMECMP_HIGH, TimerRegisters.select(0x1008, 0x1004, 0x1000));
    }

    // ------------------------------------------------------------------------------------------ software

    /**
     * The privileged specification's three stores for a new mtimecmp on RV32 (Machine Timer Registers): all ones
     * to the low word, the new high word, the new low word, each at a clock edge of its own, with edges of other
     * instructions before each.
     */
    private static boolean threeStoresRequestAnInterrupt(TimerRegisters regs, long comparand, int[] gaps) {
        var requested = regs.isInterruptRequested();
        final Word[] words = {Word.MTIMECMP_LOW, Word.MTIMECMP_HIGH, Word.MTIMECMP_LOW};
        final long[] data = {0xFFFFFFFFL, comparand >>> 32, comparand & 0xFFFFFFFFL};
        for (var i = 0; i < 3; i++) {
            for (var g = 0; g < gaps[i]; g++) {
                tick(regs);
                requested |= regs.isInterruptRequested();
            }
            store(regs, words[i], data[i]);
            requested |= regs.isInterruptRequested();
        }
        return requested;
    }

    @Test
    void theThreeStoresOfTheSpecificationRaiseNoFalseInterrupt() {
        final var rnd = new Random(20261121);
        final long[] times = {0, 1, 0x7FFFFFF0L, 0xFFFFFF00L, 0xFFFFFFF0L, 0xFFFFFFFFL, 0x100000000L, 0x1FFFFFFF0L,
            0x7FFFFFFFFFFFFF00L, 0xFFFFFFFEFFFFFFF0L, 0xFFFFFFFFFFFFF000L};
        for (var trial = 0; trial < 20000; trial++) {
            final long time = trial < 2000 ? times[trial % times.length] : rnd.nextLong();
            final int[] gaps = {rnd.nextInt(4), rnd.nextInt(4), rnd.nextInt(4)};
            // the old and the new comparand are both ahead of mtime, by more than the stores take, and
            // differ in either word or in both; so no interrupt is due before, between or after the stores
            final long room = Long.divideUnsigned(~time, 4);             // a quarter of what is left to 2^64
            if (Long.compareUnsigned(room, 64) < 0) continue;
            final long oldComparand = time + 32 + ahead(rnd, room);
            final long newComparand = time + 32 + ahead(rnd, room);
            final var regs = registers(time, oldComparand);
            final var what = String.format("mtime %016x mtimecmp %016x to %016x", time, oldComparand, newComparand);
            assertFalse(threeStoresRequestAnInterrupt(regs, newComparand, gaps), what);
            assertEquals(newComparand, regs.getMtimecmp(), what);
        }
    }

    /** A distance ahead of mtime, below room: often small, often with a low word near either end. */
    private static long ahead(Random rnd, long room) {
        final long distance = switch (rnd.nextInt(4)) {
            case 0 -> rnd.nextInt(1 << 16);
            case 1 -> (rnd.nextLong() & 0xFFFFFFFF00000000L) | rnd.nextInt(16);
            case 2 -> (rnd.nextLong() & 0xFFFFFFFF00000000L) | (0xFFFFFFF0L + rnd.nextInt(16));
            default -> rnd.nextLong();
        };
        return Long.remainderUnsigned(distance, room);
    }

    @Test
    void twoStoresWouldRaiseAFalseInterruptWhereTheThreeDoNot() {
        // the low word first: from 0x1_ffffff00 to 0x2_00000010 with mtime at 0x1_fffffe00
        final long time = 0x00000001FFFFFE00L;
        var regs = registers(time, 0x00000001FFFFFF00L);
        store(regs, Word.MTIMECMP_LOW, 0x00000010L);       // 0x1_00000010 for a moment: behind mtime
        assertTrue(regs.isInterruptRequested());
        regs = registers(time, 0x00000001FFFFFF00L);
        assertFalse(threeStoresRequestAnInterrupt(regs, 0x0000000200000010L, new int[] {0, 0, 0}));
        assertEquals(0x0000000200000010L, regs.getMtimecmp());

        // the high word first: from 0x2_00000010 down to 0x1_ffffff00 with mtime at 0x1_00000100
        regs = registers(0x0000000100000100L, 0x0000000200000010L);
        store(regs, Word.MTIMECMP_HIGH, 0x00000001L);      // 0x1_00000010 for a moment: behind mtime
        assertTrue(regs.isInterruptRequested());
        regs = registers(0x0000000100000100L, 0x0000000200000010L);
        assertFalse(threeStoresRequestAnInterrupt(regs, 0x00000001FFFFFF00L, new int[] {0, 0, 0}));
        assertEquals(0x00000001FFFFFF00L, regs.getMtimecmp());
    }

    @Test
    void theThreeStoresArmSliceAfterSliceAcross2To32() {
        // a kernel's time slice of 800 ticks, armed in its handler each time, from just below 2^32 on
        final var regs = registers(0x00000000FFFFF000L, 0x00000000FFFFF000L);
        var alarm = regs.getMtimecmp();
        for (var slice = 0; slice < 30; slice++) {
            // the interrupt comes when mtime reaches the alarm, and not before
            while (regs.getMtime() != alarm) {
                assertFalse(regs.isInterruptRequested(), Long.toHexString(regs.getMtime()));
                tick(regs);
            }
            assertTrue(regs.isInterruptRequested(), Long.toHexString(alarm));
            alarm = regs.getMtime() + 800;
            threeStoresRequestAnInterrupt(regs, alarm, new int[] {3, 1, 1});
            assertEquals(alarm, regs.getMtimecmp());
            assertFalse(regs.isInterruptRequested(), "armed: " + Long.toHexString(alarm));
        }
        assertTrue(Long.compareUnsigned(regs.getMtime(), 0x100000000L) > 0, "went past 2^32");
    }

    @Test
    void softwareThatSetsBothWordsGetsItsInterruptsFromTheStart() {
        // a kernel's first alarm and the ones after it, by the three stores, from the registers' reset values
        final var regs = new TimerRegisters(Value.FALSE);
        for (var slice = 0; slice < 50; slice++) {
            final long alarm = regs.getMtime() + 800;
            final var during = threeStoresRequestAnInterrupt(regs, alarm, new int[] {0, 0, 0});
            if (slice == 0) assertFalse(during, "none on the way from the reset value");
            assertFalse(regs.isInterruptRequested());
            assertEquals(alarm, regs.getMtimecmp());
            var edges = 0;
            while (!regs.isInterruptRequested()) {
                tick(regs);
                edges++;
            }
            assertEquals(797, edges, "the three stores took three of the 800");
            assertEquals(alarm, regs.getMtime());
        }
    }

    @Test
    void softwareOfOneWordGetsNoInterruptAfterReset() {
        // logOS up to 3.0.3: one 32-bit store of mtime's low word + 800 to mtimecmp's low word. The high word
        // keeps its reset value of all ones, so mtime never gets there: the Timer of 5.0.9 had no high word
        final var regs = new TimerRegisters(Value.FALSE);
        for (var slice = 0; slice < 50; slice++) {
            final long alarm = (regs.read(Word.MTIME_LOW) + 800) & 0xFFFFFFFFL;
            store(regs, Word.MTIMECMP_LOW, alarm);
            assertEquals(0xFFFFFFFF00000000L | alarm, regs.getMtimecmp(), "the high word is all ones");
            for (var edge = 0; edge < 2000; edge++) {
                tick(regs);
                assertFalse(regs.isInterruptRequested());
            }
        }
        // not past 2^32 either, where the low words alone would say that mtime has passed mtimecmp
        regs.setMtime(0x00000000FFFFFE00L);
        store(regs, Word.MTIMECMP_LOW, (regs.read(Word.MTIME_LOW) + 800) & 0xFFFFFFFFL);
        assertEquals(0xFFFFFFFF00000120L, regs.getMtimecmp());
        for (var edge = 0; edge < 2000; edge++) {
            tick(regs);
            assertFalse(regs.isInterruptRequested());
        }
        assertTrue(Long.compareUnsigned(regs.getMtime(), 0x100000000L) > 0, "went past 2^32");
    }

    @Test
    void theHighWordAloneBringsTheInterruptsOfOneWordBack() {
        // one store of 0 to the high word, and the one-word software's alarms come, while mtime is below 2^32
        final var regs = new TimerRegisters(Value.FALSE);
        store(regs, Word.MTIMECMP_HIGH, 0);
        assertEquals(0x00000000FFFFFFFFL, regs.getMtimecmp());
        final long alarm = (regs.read(Word.MTIME_LOW) + 800) & 0xFFFFFFFFL;
        store(regs, Word.MTIMECMP_LOW, alarm);
        var edges = 0;
        while (!regs.isInterruptRequested()) {
            tick(regs);
            edges++;
        }
        assertEquals(799, edges, "the store took one of the 800");
    }
}
