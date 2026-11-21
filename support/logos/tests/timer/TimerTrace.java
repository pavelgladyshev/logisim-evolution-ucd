import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.riscv.timer.Timer;
import com.cburch.logisim.riscv.timer.TimerRegisters;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Random;

/**
 * A record of the Java Timer for tb_timer_replay.v: the component (Timer.propagate, as the simulator calls it
 * after each change of its pins) is driven with reads, writes and resets, at clock edges and between them, and
 * each step is written as a line of hex: its flags (1 a rising clock edge after the inputs are set, 2 Reset,
 * 4 Read, 8 Write), Address, Data In, and what the Timer gives after it: Data Out, the interrupt request, mtime
 * and mtimecmp.
 *
 * <pre>java -cp classes:logisim.jar TimerTrace steps seed trace [mtimecmp address [mtime address]]</pre>
 *
 * The steps are random, in runs that software would make or might: the count left alone, single reads and
 * writes at the four words and beside them, mtimecmp set a little ahead of mtime or behind it (with the
 * specification's three stores, with two in either order, with one), mtime set just below 2^32, 2^64, 2^31 and
 * 2^63 and left to count across, and Reset at an edge, between edges and held. What the record covers is printed
 * at the end.
 */
public class TimerTrace {

  private static final Timer TIMER = new Timer();
  private static final int EDGE = 1;
  private static final int RESET = 2;
  private static final int READ = 4;
  private static final int WRITE = 8;

  /** The Timer's pins and state, standing in for the simulator. */
  private static final class Pins implements InstanceState {
    final AttributeSet attrs = TIMER.createAttributeSet();
    final Value[] ports = new Value[8];
    InstanceData data;

    void set(int pin, long value) {
      ports[pin] = Value.createKnown(pin == Timer.ADDRESS || pin == Timer.DATA_IN ? 32 : 1, value);
    }

    TimerRegisters regs() {
      return (TimerRegisters) data;
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

  /** A step: the inputs, and whether a clock edge follows. */
  private record Step(int flags, long address, long dataIn) {}

  private final Pins pins = new Pins();
  private final Random rnd;
  private final long mtimecmpAddr;
  private final long mtimeAddr;
  private final ArrayDeque<Step> queue = new ArrayDeque<>();
  private final long[] counts = new long[24];
  private static final String[] COUNT_NAMES = {
    "steps", "clock edges", "steps between edges", "edges with Reset", "steps between edges with Reset",
    "writes of mtimecmp's low word", "writes of mtime's low word", "writes of mtimecmp's high word",
    "writes of mtime's high word", "writes beside the four words", "reads of mtimecmp's low word",
    "reads of mtime's low word", "reads of mtimecmp's high word", "reads of mtime's high word",
    "reads beside the four words", "interrupt requests raised", "interrupt requests taken back",
    "carries of the count into mtime's high word", "wraps of mtime at 2^64",
    "edges with mtime within 64 of 2^32", "edges with mtime within 64 of 2^64",
    "edges with mtime at 2^32 or above", "edges with mtime == mtimecmp after them",
    "edges with mtime == mtimecmp - 1 after them"};

  private TimerTrace(long seed, long mtimecmpAddr, long mtimeAddr) {
    rnd = new Random(seed);
    this.mtimecmpAddr = mtimecmpAddr;
    this.mtimeAddr = mtimeAddr;
    pins.attrs.setValue(Timer.RISCV_MTIMECMP_ADDR, mtimecmpAddr);
    pins.attrs.setValue(Timer.RISCV_MTIME_ADDR, mtimeAddr);
    for (var pin = 0; pin < 8; pin++) pins.set(pin, 0);
    TIMER.propagate(pins);
  }

  // ------------------------------------------------------------------------------------------- the steps

  private long word(TimerRegisters.Word word) {
    return switch (word) {
      case MTIMECMP_LOW -> mtimecmpAddr;
      case MTIME_LOW -> mtimeAddr;
      case MTIMECMP_HIGH -> mtimecmpAddr + 4;
      default -> mtimeAddr + 4;
    } & 0xFFFFFFFFL;
  }

  private long anyWord() {
    return word(TimerRegisters.Word.values()[rnd.nextInt(4)]);
  }

  /** An address: one of the four words, one beside them, or any. */
  private long address() {
    final var at = anyWord();
    return switch (rnd.nextInt(16)) {
      case 0 -> (at + 1 + rnd.nextInt(3)) & 0xFFFFFFFFL;
      case 1 -> (at - 1 - rnd.nextInt(4)) & 0xFFFFFFFFL;
      case 2 -> (at + 8) & 0xFFFFFFFFL;
      case 3 -> at ^ (1L << rnd.nextInt(32));
      case 4 -> rnd.nextInt(16);
      case 5 -> rnd.nextLong() & 0xFFFFFFFFL;
      default -> at;
    };
  }

  /** A word of data: the ends of its range, the middle, a small one or any. */
  private long data() {
    return switch (rnd.nextInt(10)) {
      case 0 -> 0;
      case 1 -> 0xFFFFFFFFL;
      case 2 -> 0xFFFFFFFFL - rnd.nextInt(40);
      case 3 -> rnd.nextInt(40);
      case 4 -> 0x80000000L - rnd.nextInt(3);
      case 5 -> 0x7FFFFFFFL + rnd.nextInt(3);
      default -> rnd.nextLong() & 0xFFFFFFFFL;
    };
  }

  private void add(int flags, long address, long dataIn) {
    queue.add(new Step(flags, address, dataIn));
  }

  /**
   * Edges at which software does something else, or reads a word; now and then a step between two edges: a
   * read, a write that no edge takes, or rarely a Reset.
   */
  private void run(int edges) {
    for (var i = 0; i < edges; i++) {
      switch (rnd.nextInt(16)) {
        case 0, 1 -> add(EDGE | READ, anyWord(), data());
        case 2, 3 -> add(EDGE | READ, address(), data());
        case 4, 5 -> add(EDGE, address(), data());
        case 6 -> add(READ, anyWord(), data());
        case 7 -> add(rnd.nextInt(40) == 0 ? RESET : WRITE, anyWord(), data());
        default -> add(EDGE, rnd.nextLong() & 0xFFFFFFFFL, rnd.nextLong() & 0xFFFFFFFFL);
      }
    }
  }

  private void store(TimerRegisters.Word word, long data) {
    add(EDGE | WRITE | (rnd.nextInt(8) == 0 ? READ : 0), word(word), data & 0xFFFFFFFFL);
  }

  /** mtimecmp set to a value, as software might: the three stores, two in either order, or one. */
  private void setMtimecmp(long value) {
    final var low = TimerRegisters.Word.MTIMECMP_LOW;
    final var high = TimerRegisters.Word.MTIMECMP_HIGH;
    final var gap = rnd.nextInt(3);
    switch (rnd.nextInt(8)) {
      case 0, 1, 2 -> {
        store(low, 0xFFFFFFFFL);
        run(rnd.nextInt(gap + 1));
        store(high, value >>> 32);
        run(rnd.nextInt(gap + 1));
        store(low, value);
      }
      case 3 -> {
        store(low, value);
        run(rnd.nextInt(gap + 1));
        store(high, value >>> 32);
      }
      case 4 -> {
        store(high, value >>> 32);
        run(rnd.nextInt(gap + 1));
        store(low, value);
      }
      case 5, 6 -> store(low, value);
      default -> store(high, value >>> 32);
    }
  }

  /** mtime set to about a value: the count goes on while it is stored. */
  private void setMtime(long value) {
    final var low = TimerRegisters.Word.MTIME_LOW;
    final var high = TimerRegisters.Word.MTIME_HIGH;
    switch (rnd.nextInt(4)) {
      case 0 -> {
        store(high, value >>> 32);
        store(low, value);
      }
      case 1 -> {
        store(low, value);
        store(high, value >>> 32);
      }
      case 2 -> store(low, value);
      default -> store(high, value >>> 32);
    }
  }

  /** mtimecmp a little ahead of mtime or behind it, and the count to it and past it. */
  private void alarm() {
    final var distance = switch (rnd.nextInt(6)) {
      case 0 -> -rnd.nextInt(4);
      case 1 -> rnd.nextInt(6);
      case 2 -> 800;
      default -> rnd.nextInt(60);
    };
    setMtimecmp(pins.regs().getMtime() + distance);
    run(rnd.nextInt(distance > 100 ? 900 : 70));
  }

  /** mtime just below a power of two, an alarm beyond it, and the count across. */
  private void boundary(long power) {
    setMtime(power - 1 - rnd.nextInt(40));
    if (rnd.nextBoolean()) setMtimecmp(power - 20 + rnd.nextInt(60));
    run(20 + rnd.nextInt(80));
    if (rnd.nextBoolean()) alarm();
  }

  private void reset() {
    switch (rnd.nextInt(6)) {
      case 0 -> add(RESET, address(), data());                                         // between edges
      case 1 -> add(RESET | EDGE, address(), data());                                  // at an edge
      case 2 -> add(RESET | EDGE | WRITE, anyWord(), data());                          // with a write
      case 3 -> add(RESET | READ | (rnd.nextBoolean() ? WRITE : 0), anyWord(), data());
      default -> {                                                                     // held
        for (var i = rnd.nextInt(6); i >= 0; i--) {
          add(RESET | (rnd.nextBoolean() ? EDGE : 0) | (rnd.nextBoolean() ? WRITE : 0)
              | (rnd.nextBoolean() ? READ : 0), address(), data());
        }
      }
    }
  }

  /** The next run of steps. */
  private void more() {
    final var r = rnd.nextInt(100);
    if (r < 18) {
      run(1 + rnd.nextInt(rnd.nextInt(8) == 0 ? 400 : 30));
    } else if (r < 40) {
      // a single step of any kind
      add((rnd.nextInt(10) == 0 ? 0 : EDGE) | (rnd.nextBoolean() ? READ : 0) | (rnd.nextBoolean() ? WRITE : 0),
          address(), data());
    } else if (r < 62) {
      alarm();
    } else if (r < 70) {
      boundary(1L << 32);
    } else if (r < 77) {
      boundary(0);                                    // 2^64
    } else if (r < 80) {
      boundary(rnd.nextBoolean() ? 1L << 31 : 1L << 63);
    } else if (r < 82) {
      boundary((long) (1 + rnd.nextInt(3)) << 32);     // a multiple of 2^32
    } else if (r < 89) {
      reset();
    } else if (r < 94) {
      // between edges: what is read, and writes that no edge takes
      for (var i = rnd.nextInt(5); i >= 0; i--) {
        add((rnd.nextBoolean() ? READ : 0) | (rnd.nextInt(3) == 0 ? WRITE : 0), address(), data());
      }
    } else {
      // any 64 bits in the registers
      for (final var word : TimerRegisters.Word.values()) {
        if (word != TimerRegisters.Word.NONE && rnd.nextBoolean()) store(word, rnd.nextLong());
      }
    }
  }

  // ------------------------------------------------------------------------------------------- the record

  private String outputs() {
    final var out = pins.ports[Timer.DATA_OUT];
    final var irq = pins.ports[Timer.INTERRUPT_OUT];
    if (!out.isFullyDefined() || !irq.isFullyDefined()) throw new IllegalStateException("an output is not driven");
    return String.format("%08x %x %016x %016x", out.toLongValue(), irq.toLongValue(), pins.regs().getMtime(),
        pins.regs().getMtimecmp());
  }

  private void step(Step step, PrintWriter out) {
    final var flags = step.flags();
    final var before = pins.regs().getMtime();
    final var requested = pins.regs().isInterruptRequested();
    final var selected = TimerRegisters.select(step.address(), mtimecmpAddr, mtimeAddr);
    pins.set(Timer.RESET, (flags & RESET) != 0 ? 1 : 0);
    pins.set(Timer.READ_ENABLE, (flags & READ) != 0 ? 1 : 0);
    pins.set(Timer.WRITE_ENABLE, (flags & WRITE) != 0 ? 1 : 0);
    pins.set(Timer.ADDRESS, step.address());
    pins.set(Timer.DATA_IN, step.dataIn());
    TIMER.propagate(pins);
    if ((flags & EDGE) != 0) {
      pins.set(Timer.CLOCK, 1);
      TIMER.propagate(pins);
    }
    final var outputs = outputs();
    out.printf("%x %08x %08x %s%n", flags, step.address(), step.dataIn(), outputs);
    if ((flags & EDGE) != 0) {
      // the falling edge changes nothing
      pins.set(Timer.CLOCK, 0);
      TIMER.propagate(pins);
      if (!outputs.equals(outputs())) throw new IllegalStateException("the falling edge changed " + outputs);
    }

    final var after = pins.regs().getMtime();
    final var edge = (flags & EDGE) != 0;
    final var reset = (flags & RESET) != 0;
    final var none = selected == TimerRegisters.Word.NONE;
    counts[0]++;
    counts[edge ? 1 : 2]++;
    if (reset) counts[edge ? 3 : 4]++;
    if (edge && !reset && (flags & WRITE) != 0) counts[none ? 9 : 5 + selected.ordinal()]++;
    if ((flags & READ) != 0) counts[none ? 14 : 10 + selected.ordinal()]++;
    if (pins.regs().isInterruptRequested() != requested) counts[requested ? 16 : 15]++;
    if (edge && !reset && (flags & WRITE) == 0) {
      if ((before & 0xFFFFFFFFL) == 0xFFFFFFFFL && before != -1L) counts[17]++;
      if (before == -1L) counts[18]++;
    }
    if (edge) {
      if (Long.compareUnsigned(after - (1L << 32) + 64, 128) < 0) counts[19]++;
      if (Long.compareUnsigned(after + 64, 128) < 0) counts[20]++;
      if (Long.compareUnsigned(after, 1L << 32) >= 0) counts[21]++;
      if (after == pins.regs().getMtimecmp()) counts[22]++;
      if (after == pins.regs().getMtimecmp() - 1) counts[23]++;
    }
  }

  public static void main(String[] args) throws IOException {
    final var steps = Integer.parseInt(args[0]);
    final var seed = Long.parseLong(args[1]);
    final var mtimecmpAddr = args.length > 3 ? Long.decode(args[3]) : Timer.RISCV_MTIMECMP_ADDR_DEFAULT;
    final var mtimeAddr = args.length > 4 ? Long.decode(args[4]) : Timer.RISCV_MTIME_ADDR_DEFAULT;
    final var trace = new TimerTrace(seed, mtimecmpAddr, mtimeAddr);
    try (final var out = new PrintWriter(new BufferedWriter(new FileWriter(args[2])))) {
      // what the Timer gives before its first edge
      trace.step(new Step(READ, mtimecmpAddr & 0xFFFFFFFFL, 0), out);
      trace.step(new Step(READ, mtimeAddr & 0xFFFFFFFFL, 0), out);
      for (var i = 2; i < steps; i++) {
        while (trace.queue.isEmpty()) trace.more();
        trace.step(trace.queue.remove(), out);
      }
    }
    System.out.printf("%s: mtimecmp at 0x%08x, mtime at 0x%08x, seed %d%n", args[2], mtimecmpAddr & 0xFFFFFFFFL,
        mtimeAddr & 0xFFFFFFFFL, seed);
    for (var i = 0; i < COUNT_NAMES.length; i++) System.out.printf("%10d %s%n", trace.counts[i], COUNT_NAMES[i]);
  }
}
