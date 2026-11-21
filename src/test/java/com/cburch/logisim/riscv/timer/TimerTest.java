/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.timer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.proj.Project;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The Timer component at its pins, as the simulator drives it: the four words on the data bus, what a rising clock
 * edge does and what nothing else does, Reset at an edge and between edges, the interrupt request, the attributes
 * that circuit files name, the registers as it shows them, and the values that its Verilog shares with it.
 */
class TimerTest {

    private static final long MTIMECMP = 0x02004000L;
    private static final long MTIME = 0x0200BFF8L;
    private static final Timer TIMER = new Timer();

    /** The Timer's pins and state, standing in for the simulator: propagate() after every change of a pin. */
    private static final class Pins implements InstanceState {
        private final AttributeSet attrs = TIMER.createAttributeSet();
        private final Value[] ports = new Value[8];
        private InstanceData data;

        Pins() {
            ports[Timer.ADDRESS] = Value.createKnown(32, 0);
            ports[Timer.DATA_IN] = Value.createKnown(32, 0);
            for (final var pin : new int[] {Timer.READ_ENABLE, Timer.WRITE_ENABLE, Timer.RESET, Timer.CLOCK}) {
                ports[pin] = Value.FALSE;
            }
            TIMER.propagate(this);
        }

        Pins set(int pin, Value value) {
            ports[pin] = value;
            TIMER.propagate(this);
            return this;
        }

        Pins set(int pin, long value) {
            final var width = pin == Timer.ADDRESS || pin == Timer.DATA_IN ? 32 : 1;
            return set(pin, Value.createKnown(width, value));
        }

        /** A clock cycle: the rising edge, at which the Timer acts, and the falling edge. */
        Pins cycle() {
            return set(Timer.CLOCK, 1).set(Timer.CLOCK, 0);
        }

        Pins cycles(int count) {
            for (var i = 0; i < count; i++) cycle();
            return this;
        }

        /** A store by the CPU: the bus cycle is there before the edge and gone after it. */
        Pins store(long address, long word) {
            set(Timer.ADDRESS, address).set(Timer.DATA_IN, word).set(Timer.WRITE_ENABLE, 1);
            cycle();
            return set(Timer.WRITE_ENABLE, 0);
        }

        /** A load by the CPU: what Data Out gives for the address, with no clock edge. */
        long load(long address) {
            set(Timer.ADDRESS, address).set(Timer.READ_ENABLE, 1);
            final var word = dataOut();
            set(Timer.READ_ENABLE, 0);
            return word;
        }

        long dataOut() {
            assertTrue(ports[Timer.DATA_OUT].isFullyDefined());
            return ports[Timer.DATA_OUT].toLongValue();
        }

        boolean irq() {
            assertTrue(ports[Timer.INTERRUPT_OUT].isFullyDefined());
            return ports[Timer.INTERRUPT_OUT] == Value.TRUE;
        }

        TimerRegisters regs() {
            return (TimerRegisters) data;
        }

        long mtime() {
            return load(MTIME) | (load(MTIME + 4) << 32);
        }

        long mtimecmp() {
            return load(MTIMECMP) | (load(MTIMECMP + 4) << 32);
        }

        @Override
        public Value getPortValue(int portIndex) {
            return ports[portIndex];
        }

        @Override
        public void setPort(int portIndex, Value value, int delay) {
            ports[portIndex] = value;
        }

        @Override
        public <E> E getAttributeValue(Attribute<E> attr) {
            return attrs.getValue(attr);
        }

        @Override
        public AttributeSet getAttributeSet() {
            return attrs;
        }

        @Override
        public InstanceData getData() {
            return data;
        }

        @Override
        public void setData(InstanceData value) {
            data = value;
        }

        @Override
        public InstanceFactory getFactory() {
            return TIMER;
        }

        @Override
        public void fireInvalidated() {}

        @Override
        public Instance getInstance() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getPortIndex(Port port) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Project getProject() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getTickCount() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCircuitRoot() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isPortConnected(int portIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CircuitState createCircuitSubstateFor(Circuit circ) {
            throw new UnsupportedOperationException();
        }
    }

    // ------------------------------------------------------------------------------------------ circuit files

    @Test
    void theAttributesKeepTheirNamesAndDefaults() {
        final var attrs = TIMER.createAttributeSet();
        assertEquals(3, attrs.getAttributes().size());
        assertEquals(0x02004000L, attrs.getValue(Timer.RISCV_MTIMECMP_ADDR));
        assertEquals(0x0200BFF8L, attrs.getValue(Timer.RISCV_MTIME_ADDR));
        assertEquals(false, attrs.getValue(Timer.ATTR_HEX_REGS));
        assertEquals(Timer.RISCV_MTIMECMP_ADDR, attrs.getAttribute("mtimecmpaddr"));
        assertEquals(Timer.RISCV_MTIME_ADDR, attrs.getAttribute("mtimeaddr"));
        assertEquals(Timer.ATTR_HEX_REGS, attrs.getAttribute("hexRegisters"));
        assertEquals("Timer", TIMER.getName());
    }

    @Test
    void thePinsAreWhereTheyWere() {
        final int[][] pins = {
            {Timer.ADDRESS, -40, -10, 32}, {Timer.READ_ENABLE, -40, 0, 1}, {Timer.WRITE_ENABLE, -40, 10, 1},
            {Timer.DATA_OUT, 50, 0, 32}, {Timer.INTERRUPT_OUT, 50, 20, 1}, {Timer.RESET, -10, 50, 1},
            {Timer.CLOCK, 10, 50, 1}, {Timer.DATA_IN, 40, 50, 32}};
        assertEquals(pins.length, TIMER.getPorts().size());
        final var origin = com.cburch.logisim.data.Location.create(0, 0, false);
        for (final var pin : pins) {
            final var end = TIMER.getPorts().get(pin[0]).toEnd(origin, TIMER.createAttributeSet());
            assertEquals(com.cburch.logisim.data.Location.create(pin[1], pin[2], false), end.getLocation(), "pin " + pin[0]);
            assertEquals(pin[3], end.getWidth().getWidth(), "pin " + pin[0]);
        }
        final var bounds = TIMER.getOffsetBounds(TIMER.createAttributeSet());
        assertEquals(com.cburch.logisim.data.Bounds.create(-40, -116, 90, 166), bounds);
    }

    @Test
    void everyPinHasATipAndResetsNamesTheResetValue() {
        for (final var port : TIMER.getPorts()) {
            final var tip = port.getToolTip();
            assertNotNull(tip);
            assertTrue(tip.contains(" ") && !tip.contains("%"), tip);
        }
        final var reset = TIMER.getPorts().get(Timer.RESET).getToolTip();
        assertTrue(reset.contains(String.format("0x%016x", TimerRegisters.MTIMECMP_RESET)), reset);
    }

    // ------------------------------------------------------------------------------------------ the data bus

    @Test
    void beforeAnyEdgeTheRegistersAreAtTheirResetValuesAndBothOutputsAreDriven() {
        final var pins = new Pins();
        assertEquals(0, pins.dataOut());
        assertEquals(false, pins.irq());
        assertEquals(0, pins.mtime());
        assertEquals(TimerRegisters.MTIMECMP_RESET, pins.mtimecmp());
        assertEquals(0xFFFFFFFFL, pins.load(MTIMECMP));
        assertEquals(0xFFFFFFFFL, pins.load(MTIMECMP + 4));
    }

    @Test
    void theFourWordsAreAtTheAddressesAndFourAboveThem() {
        final var pins = new Pins();
        pins.regs().setMtime(0x1111111122222222L);
        pins.regs().setMtimecmp(0x3333333344444444L);
        assertEquals(0x44444444L, pins.load(0x02004000L));
        assertEquals(0x33333333L, pins.load(0x02004004L));
        assertEquals(0x22222222L, pins.load(0x0200BFF8L));
        assertEquals(0x11111111L, pins.load(0x0200BFFCL));
    }

    @Test
    void dataOutIs0UnlessAWordIsRead() {
        final var pins = new Pins();
        pins.regs().setMtime(0x1111111122222222L);
        pins.regs().setMtimecmp(0x3333333344444444L);
        pins.set(Timer.ADDRESS, MTIME).set(Timer.READ_ENABLE, 0);
        assertEquals(0, pins.dataOut(), "not read");
        for (final long address : new long[] {0, 0x02004008L, 0x02003FFCL, 0x0200BFF4L, 0x0200C000L, 0x02004001L}) {
            assertEquals(0, pins.load(address), Long.toHexString(address));
        }
        pins.set(Timer.READ_ENABLE, 1).set(Timer.ADDRESS, Value.createUnknown(BitWidth.create(32)));
        assertEquals(0, pins.dataOut(), "no address");
        pins.set(Timer.ADDRESS, MTIME).set(Timer.READ_ENABLE, Value.UNKNOWN);
        assertEquals(0, pins.dataOut(), "Read is not 1");
    }

    @Test
    void storesChangeTheirWordAtTheRisingEdge() {
        final var pins = new Pins();
        pins.store(MTIMECMP, 0x44444444L);
        assertEquals(0xFFFFFFFF44444444L, pins.mtimecmp());
        pins.store(MTIMECMP + 4, 0x33333333L);
        assertEquals(0x3333333344444444L, pins.mtimecmp());
        assertEquals(2, pins.mtime(), "two edges");
        pins.store(MTIME + 4, 0x11111111L);
        assertEquals(0x1111111100000003L, pins.mtime());
        pins.store(MTIME, 0x22222222L);
        assertEquals(0x1111111122222223L, pins.mtime(), "the store, and the count of its edge");
        assertEquals(0x3333333344444444L, pins.mtimecmp());
    }

    @Test
    void nothingIsStoredWithoutWriteOrWithoutARisingEdge() {
        final var pins = new Pins();
        pins.set(Timer.ADDRESS, MTIMECMP).set(Timer.DATA_IN, 5).set(Timer.WRITE_ENABLE, 0).cycle();
        assertEquals(TimerRegisters.MTIMECMP_RESET, pins.mtimecmp(), "Write is 0");
        pins.set(Timer.ADDRESS, MTIMECMP).set(Timer.WRITE_ENABLE, Value.UNKNOWN).cycle();
        assertEquals(TimerRegisters.MTIMECMP_RESET, pins.mtimecmp(), "Write is not 1");
        // Write comes and goes while the clock is low, and again while it is high
        pins.set(Timer.ADDRESS, MTIMECMP).set(Timer.WRITE_ENABLE, 1).set(Timer.WRITE_ENABLE, 0);
        pins.set(Timer.CLOCK, 1).set(Timer.WRITE_ENABLE, 1).set(Timer.DATA_IN, 6).set(Timer.WRITE_ENABLE, 0);
        pins.set(Timer.WRITE_ENABLE, 1).set(Timer.CLOCK, 0).set(Timer.WRITE_ENABLE, 0);
        assertEquals(TimerRegisters.MTIMECMP_RESET, pins.mtimecmp(), "no rising edge with Write 1");
        assertEquals(3, pins.mtime());
        // an address that selects no word, and one that is not defined
        pins.store(0x02004008L, 7).store(0x0200BFF4L, 7);
        pins.set(Timer.ADDRESS, Value.createUnknown(BitWidth.create(32))).set(Timer.WRITE_ENABLE, 1).cycle();
        assertEquals(TimerRegisters.MTIMECMP_RESET, pins.mtimecmp());
        assertEquals(6, pins.mtime());
    }

    @Test
    void mtimeCountsAtRisingEdgesOnly() {
        final var pins = new Pins();
        pins.set(Timer.CLOCK, 1);
        assertEquals(1, pins.regs().getMtime());
        pins.set(Timer.ADDRESS, 4).set(Timer.DATA_IN, 9).set(Timer.READ_ENABLE, 1).set(Timer.READ_ENABLE, 0);
        assertEquals(1, pins.regs().getMtime(), "other pins change");
        pins.set(Timer.CLOCK, 0);
        assertEquals(1, pins.regs().getMtime(), "the falling edge");
        pins.set(Timer.CLOCK, Value.UNKNOWN).set(Timer.CLOCK, 1);
        assertEquals(1, pins.regs().getMtime(), "not from 0 to 1");
        pins.set(Timer.CLOCK, 0).set(Timer.CLOCK, 1);
        assertEquals(2, pins.regs().getMtime());
    }

    @Test
    void whatIsReadFollowsTheEdgeAtOnce() {
        final var pins = new Pins();
        pins.set(Timer.ADDRESS, MTIME).set(Timer.READ_ENABLE, 1);
        assertEquals(0, pins.dataOut());
        pins.set(Timer.CLOCK, 1);
        assertEquals(1, pins.dataOut(), "the count of this edge, while the clock is still high");
        pins.set(Timer.CLOCK, 0);
        assertEquals(1, pins.dataOut());
        // and the store of this edge
        pins.set(Timer.ADDRESS, MTIMECMP).set(Timer.DATA_IN, 0x55L).set(Timer.WRITE_ENABLE, 1).set(Timer.CLOCK, 1);
        assertEquals(0x55L, pins.dataOut());
    }

    @Test
    void theCountCarriesIntoTheHighWordOnTheBus() {
        final var pins = new Pins();
        pins.store(MTIME, 0xFFFFFFFDL);
        assertEquals(0x00000000FFFFFFFEL, pins.mtime());
        pins.cycle();
        assertEquals(0xFFFFFFFFL, pins.load(MTIME));
        assertEquals(0, pins.load(MTIME + 4));
        pins.cycle();
        assertEquals(0, pins.load(MTIME));
        assertEquals(1, pins.load(MTIME + 4));
    }

    // ------------------------------------------------------------------------------------------ the interrupt

    @Test
    void theInterruptRequestFollowsTheComparisonOf64Bits() {
        final var pins = new Pins();
        pins.store(MTIME, 0xFFFFFFF0L);                     // 0x0_fffffff1 after the edge
        pins.store(MTIMECMP + 4, 1).store(MTIMECMP, 2);     // mtimecmp 0x1_00000002
        assertEquals(0x00000000FFFFFFF3L, pins.mtime());
        for (var time = 0x00000000FFFFFFF3L; time < 0x0000000100000002L; time++) {
            assertEquals(false, pins.irq(), Long.toHexString(time));
            pins.cycle();
        }
        assertEquals(0x0000000100000002L, pins.mtime());
        assertEquals(true, pins.irq(), "mtime has reached mtimecmp");
        pins.cycles(5);
        assertEquals(true, pins.irq(), "and stays");
        pins.store(MTIMECMP, 0x100);                         // 0x1_00000100: ahead again
        assertEquals(false, pins.irq());
        pins.store(MTIMECMP + 4, 0);                         // 0x0_00000100: behind
        assertEquals(true, pins.irq());
    }

    @Test
    void theInterruptRequestIsThereAtTheEdgeThatBringsIt() {
        final var pins = new Pins();
        pins.store(MTIMECMP, 5);
        assertEquals(false, pins.irq(), "the high word is still all ones");
        pins.cycles(10);
        assertEquals(false, pins.irq(), "mtime 11, and mtimecmp 0xffffffff_00000005");
        pins.store(MTIME, 0).store(MTIMECMP + 4, 0);
        assertEquals(2, pins.regs().getMtime());
        pins.cycles(2);
        assertEquals(false, pins.irq(), "mtime 4");
        pins.set(Timer.CLOCK, 1);
        assertEquals(true, pins.irq(), "mtime 5, while the clock is still high");
        // a store that makes mtimecmp equal to the mtime of its own edge
        final var equal = new Pins();
        equal.store(MTIMECMP + 4, 0).cycles(8);
        equal.set(Timer.ADDRESS, MTIMECMP).set(Timer.DATA_IN, 10).set(Timer.WRITE_ENABLE, 1).set(Timer.CLOCK, 1);
        assertEquals(10, equal.regs().getMtime());
        assertEquals(true, equal.irq());
        final var ahead = new Pins();
        ahead.store(MTIMECMP + 4, 0).cycles(8);
        ahead.set(Timer.ADDRESS, MTIMECMP).set(Timer.DATA_IN, 11).set(Timer.WRITE_ENABLE, 1).set(Timer.CLOCK, 1);
        assertEquals(10, ahead.regs().getMtime());
        assertEquals(false, ahead.irq());
    }

    // ------------------------------------------------------------------------------------------ reset

    @Test
    void resetAtAnEdgePutsTheRegistersBackAndNothingElse() {
        final var pins = new Pins();
        pins.store(MTIMECMP + 4, 7).store(MTIME + 4, 7).store(MTIMECMP, 3).cycles(10);
        assertEquals(0x000000070000000DL, pins.regs().getMtime());
        assertEquals(true, pins.irq());
        pins.set(Timer.RESET, 1);
        pins.set(Timer.ADDRESS, MTIMECMP).set(Timer.DATA_IN, 0x1234).set(Timer.WRITE_ENABLE, 1).set(Timer.CLOCK, 1);
        assertEquals(0, pins.regs().getMtime(), "no count at the edge");
        assertEquals(TimerRegisters.MTIMECMP_RESET, pins.regs().getMtimecmp(), "no store at the edge");
        assertEquals(false, pins.irq());
        pins.set(Timer.CLOCK, 0).set(Timer.WRITE_ENABLE, 0);
        // Reset goes while the clock is high after another edge: mtime is 0 until the next one
        pins.set(Timer.CLOCK, 1).set(Timer.RESET, 0);
        assertEquals(0, pins.regs().getMtime());
        pins.set(Timer.CLOCK, 0).cycle();
        assertEquals(1, pins.regs().getMtime());
    }

    @Test
    void resetBetweenEdgesHoldsTheRegistersAndTakesTheInterruptRequestBack() {
        final var pins = new Pins();
        pins.store(MTIMECMP + 4, 0).store(MTIMECMP, 3).cycles(10);
        assertEquals(true, pins.irq());
        pins.set(Timer.RESET, 1);
        assertEquals(0, pins.regs().getMtime(), "at once, without an edge");
        assertEquals(TimerRegisters.MTIMECMP_RESET, pins.regs().getMtimecmp());
        assertEquals(false, pins.irq());
        pins.set(Timer.RESET, 0);
        assertEquals(0, pins.regs().getMtime());
        pins.cycle();
        assertEquals(1, pins.regs().getMtime());
        // while the clock is high, too
        pins.set(Timer.CLOCK, 1).set(Timer.RESET, 1).set(Timer.RESET, 0);
        assertEquals(0, pins.regs().getMtime());
        pins.set(Timer.RESET, Value.UNKNOWN).set(Timer.CLOCK, 0).cycle();
        assertEquals(1, pins.regs().getMtime(), "a Reset that is not 1 is none");
    }

    // ------------------------------------------------------------------------------------------ the display

    @Test
    void theRegistersAreShownInFull() {
        assertArrayEquals(new String[] {"0x00000000", "00000000"}, Timer.registerLines(0, true));
        assertArrayEquals(new String[] {"0x00000000", "ffffffff"}, Timer.registerLines(0xFFFFFFFFL, true));
        assertArrayEquals(new String[] {"0x00000001", "00000000"}, Timer.registerLines(0x100000000L, true));
        assertArrayEquals(new String[] {"0x89abcdef", "01234567"}, Timer.registerLines(0x89ABCDEF01234567L, true));
        assertArrayEquals(new String[] {"0xffffffff", "ffffffff"}, Timer.registerLines(-1L, true));
        assertArrayEquals(new String[] {"0"}, Timer.registerLines(0, false));
        assertArrayEquals(new String[] {"800"}, Timer.registerLines(800, false));
        assertArrayEquals(new String[] {"2147483648"}, Timer.registerLines(0x80000000L, false));
        assertArrayEquals(new String[] {"4294967295"}, Timer.registerLines(0xFFFFFFFFL, false));
        assertArrayEquals(new String[] {"4294967296"}, Timer.registerLines(0x100000000L, false));
        assertArrayEquals(new String[] {"9999999999"}, Timer.registerLines(9999999999L, false));
        assertArrayEquals(new String[] {"1", "0000000000"}, Timer.registerLines(10000000000L, false));
        assertArrayEquals(new String[] {"922337203", "6854775808"}, Timer.registerLines(Long.MIN_VALUE, false));
        assertArrayEquals(new String[] {"1844674407", "3709551615"}, Timer.registerLines(-1L, false));
    }

    // ------------------------------------------------------------------------------------------ the Verilog

    private static String resource(String name) throws IOException {
        try (final var in = Timer.class.getResourceAsStream("/resources/logisim/hdl/riscv/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long parameter(String verilog, String name) {
        final var m = Pattern.compile("parameter \\[\\d+:0\\] " + name + "\\s*= \\d+'h([0-9a-f]+)").matcher(verilog);
        assertTrue(m.find(), name);
        return Long.parseUnsignedLong(m.group(1), 16);
    }

    @Test
    void theVerilogHasTheComponentsAddressesAndResetValue() throws IOException {
        final var verilog = resource("riscvTimer.v");
        assertEquals(Timer.RISCV_MTIMECMP_ADDR_DEFAULT, parameter(verilog, "MTIMECMP_ADDR"));
        assertEquals(Timer.RISCV_MTIME_ADDR_DEFAULT, parameter(verilog, "MTIME_ADDR"));
        assertEquals(TimerRegisters.MTIMECMP_RESET, parameter(verilog, "MTIMECMP_RESET"));
    }

    @Test
    void theGeneratedModuleGivesTheVerilogTheResetValue() {
        final var module = new TimerHdlGeneratorFactory().getModuleFunctionality(null, TIMER.createAttributeSet());
        final var text = String.join("\n", module.get());
        final var expected = String.format(".MTIMECMP_RESET(64'h%016x)", TimerRegisters.MTIMECMP_RESET);
        assertTrue(text.contains(expected), text);
        assertTrue(text.contains(".MTIMECMP_ADDR(mtimecmpAddr)") && text.contains(".MTIME_ADDR(mtimeAddr)"), text);
        assertNull(TIMER.createAttributeSet().getAttribute("mtimecmpReset"), "not an attribute");
    }
}
