/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.riscv.cpu.csrs.*;
import com.cburch.logisim.riscv.cpu.gdb.DebuggerRequest;

import javax.swing.*;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.MIP;
import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.MIE;

/** Represents the state of a cpu. */
public class rv32imData implements InstanceData, Cloneable {

  /** The last values observed. */
  private Value lastClock;

  static final Value HiZ32 = Value.createUnknown(BitWidth.create(32));

  /** Output values */

  static final Value ALL1s = Value.createKnown(32,0xffffffff);
  static final Value ALL0s = Value.createKnown(32,0x0);
  static final Value [] byteMasks = {Value.createKnown(32,0xffffff00),
          Value.createKnown(32,0xffff00ff),
          Value.createKnown(32,0xff00ffff),
          Value.createKnown(32,0x00ffffff)};
  static final Value [] hwMasks = {Value.createKnown(32,0xffff0000),
          Value.createKnown(32,0x0000ffff)};

  private Value address;          // value to be placed on the address bus;
  private long outputData;        // value to be placed on data bus, possibly after shifting and mixing
  private int outputDataWidth;    // width of the data to be written in bytes (1,2,or 4)
  private Value memRead;
  private Value memWrite;
  private Value waitAck = Value.FALSE;

  /** Boolean flags */
  private boolean fetching;
  private boolean addressing;

  /** Registers */
  private final ProgramCounter pc;
  private final InstructionRegister ir;
  private final IntegerRegisters x;

  /** CSR registers */
  private final ControlAndStatusRegisters csr;

  /** Enum representing CPU states */
  private CPUState cpuState;

  public enum CPUState {
    RUNNING,
    STOPPED,
    SINGLE_STEP
  }

  /** GDB server */
  private GDBServer gdbServer = null;
  private DebuggerRequest debuggerRequest = null;

  private Map<Long, Boolean> breakpoints;
  private boolean breakpointsEnabled;

  /** Memory cache */
  private final MemoryCache cache = new MemoryCache();
  private boolean cache_hit = false;

  // More To Do

  public rv32imData(Value lastClock, long resetAddress, int port, boolean startGDBServer, CPUState initialCpuState, GDBServer gdbServerAttr) {

    // initial values for registers
    this.lastClock = lastClock;
    this.pc = new ProgramCounter(resetAddress);
    this.ir = new InstructionRegister(0x13); // Initial value 0x13 is opcode for addi x0,x0,0 (nop)
    this.x = new IntegerRegisters();
    this.csr = new ControlAndStatusRegisters();
    this.cpuState = initialCpuState;

    if(startGDBServer) {
      try {
        this.gdbServer = gdbServerAttr;
        this.gdbServer.startGDBServer(port,this);
      } catch (IOException ex) {
        String message = "Cannot start GDB server at port "+port;
        SwingUtilities.invokeLater(
                ()->OptionPane.showMessageDialog(null,message,
                        "GDB Server",
                        OptionPane.ERROR_MESSAGE));
        this.gdbServer = null;
      }
    }

    // In the first clock cycle we are fetching the first instruction
    fetchNextInstruction();

    this.breakpoints = new HashMap<>();
    this.breakpointsEnabled = true;
  }

  /**
   * Set up outputs to fetch next instruction
   */
  public void fetchNextInstruction()
  {
    fetching = true;
    addressing = false;

    long pcVal = pc.get();

    if (cache.isValid(pcVal)) {
      cache_hit = true;
      // The output data bus is in High Z
      address = HiZ32;
      outputData = 0;
      outputDataWidth = 0;
      memRead = Value.FALSE;
      memWrite = Value.FALSE;
    } else {
      // Values for outputs fetching instruction
      cache_hit = false;
      address = Value.createKnown(32, pcVal);
      outputData = 0;     // The output data bus is in High Z
      outputDataWidth = 0;    // all 4 bytes of the output
      memRead = Value.TRUE;   // MemRead active
      memWrite = Value.FALSE; // MemWrite not active
    }
  }

  /**
   * Retrieves the state associated with this counter in the circuit state, generating the state if
   * necessary.
   */
  public static rv32imData get(InstanceState state) {
    synchronized (rv32imData.class) {
      rv32imData ret = (rv32imData) state.getData();
      if (ret == null) {
        // If it doesn't yet exist, then we'll set it up with our default
        // values and put it into the circuit state so it can be retrieved
        // in future propagations.
        ret = new rv32imData(null, state.getAttributeValue(rv32im.ATTR_RESET_ADDR),
                state.getAttributeValue(rv32im.ATTR_TCP_PORT),
                state.getAttributeValue(rv32im.ATTR_GDB_SERVER_RUNNING),
                state.getAttributeValue(rv32im.ATTR_GDB_SERVER_RUNNING) ? CPUState.STOPPED : CPUState.RUNNING,
                (GDBServer)state.getAttributeValue(rv32im.ATTR_GDB_SERVER));
        state.setData(ret);
      }
      return ret;
    }
  }

  /** Returns a copy of this object. */
  @Override
  public Object clone() {
    // We can just use what super.clone() returns: The only instance
    // variables are
    // Value objects, which are immutable, so we don't care that both the
    // copy
    // and the copied refer to the same Value objects. If we had mutable
    // instance
    // variables, then of course we would need to clone them.
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      return null;
    }
  }
  /** Updates the last clock observed, returning true if triggered. */
  public boolean updateClock(Value value) {
    Value old = lastClock;
    lastClock = value;
    return old == Value.FALSE && value == Value.TRUE;
  }

  /** reset CPU state to initial values */
  public void reset(long pcInit) {
    pc.set(pcInit);
  }

  public void update(long dataIn, long timerInterruptRequest, long externalInterruptRequest, long waitRequest) {

    boolean result = false;

    if (fetching) {
      // check for and process debugger requests only during instruction fetch phase,
      // so that instruction execution is not stopped midway.
      synchronized (this) {
        if (debuggerRequest != null) {
          result = debuggerRequest.process(dataIn);
          if (result) {
            debuggerRequest = null;
          }
        }
      }
      // always return after finishing processing of a debugger request to allow for next instruction addressing when resuming.
      if (result) { return; }
    }

    if (cpuState == CPUState.STOPPED) {
      // do not perform instructions or state updates.
      return;
    }

    // Check for breakpoint before executing instruction
    if (isBreakpointHit()) {
      cpuState = CPUState.STOPPED;
      addDebuggerResponse("T05"); // Signal 5 is SIGTRAP
      return;
    }

    // update interrupt pending bits in MIP CSR to reflect the state of input pins
    MIP_CSR mip = (MIP_CSR) MMCSR.getCSR(this, MIP);
    mip.MTIP.set(timerInterruptRequest);
    mip.MEIP.set(externalInterruptRequest);

    // Check for external interrupts first
    if (isExternalInterruptPending()) {
      TrapHandler.handle(this, MCAUSE_CSR.TRAP_CAUSE.MACHINE_EXTERNAL_INTERRUPT);
      fetchNextInstruction();
      return;
    }

    // Check for timer interrupts as the second priority
    if (isTimerInterruptPending()) {
      TrapHandler.handle(this, MCAUSE_CSR.TRAP_CAUSE.MACHINE_TIMER_INTERRUPT);
      fetchNextInstruction();
      return;
    }

    if (fetching) {
      long pcVal = pc.get();
      System.out.println(pcVal + " " + cache.isValid(pcVal));
      if (cache.isValid(pcVal)) {
        System.out.println("Cache hit: " + cache.get(pcVal ));
        ir.set(cache.get(pcVal));
      } else {
        System.out.println("Cache miss: Fetching from memory");
        cache_hit = false;
        ir.set(dataIn);
        cache.update(pcVal, dataIn);
      }
    }

    switch(ir.opcode()) {
      case 0x13:  // I-type arithmetic instruction
        ArithmeticInstruction.executeImmediate(this);
        fetchNextInstruction();
        break;
      case 0x33:  // R-type arithmetic & multiplication and division instructions
        ArithmeticInstruction.executeRegister(this);
        fetchNextInstruction();
        break;
      case 0x03:  // load instruction (I-type)
        if(!addressing) {
          LoadInstruction.performAddressing(this);
        } else {
          LoadInstruction.latch(this, dataIn);
          fetchNextInstruction();
        }
        break;
      case 0x23:  // storing instruction (S-type)
        if(!addressing) {
          StoreInstruction.performAddressing(this);
        } else {
          pc.increment();
          fetchNextInstruction();
        }
        break;
      case 0x63:  // branch instruction (B-type)
        BranchInstruction.execute(this);
        fetchNextInstruction();
        break;
      case 0x6F:  // Jump And Link (J-Type)
        JumpAndLink.link(this); //jal rd,label
        pc.set((pc.get() + ir.imm_J()) & 0xffffffffL);
        fetchNextInstruction();
        break;
      case 0x67:  // Jump And Link Reg (I-Type)
        JumpAndLink.link(this); //jalr rd,rs1,imm_I
        pc.set((getX(ir.rs1()) + ir.imm_I()) & 0xfffffffeL);
        fetchNextInstruction();
        break;
      case 0x37:  // Load Upper Immediate (U-type)
        setX(ir.rd(),ir.imm_U()); //lui rd,imm_U
        pc.increment();
        fetchNextInstruction();
        break;
      case 0x17:  // Add Upper Immediate to PC (U-type)
        setX(ir.rd(), (pc.get() + ir.imm_U())); //auipc rd,imm_U
        pc.increment();
        fetchNextInstruction();
        break;
      case 0x73:  // System instructions
        SystemInstruction.execute(this);
        fetchNextInstruction();
        break;
      default:  // Unknown instruction
        TrapHandler.throwIllegalInstructionException(this);
    }

    // After updating PC, check for breakpoint again
    if (isBreakpointHit() || (fetching && (cpuState == CPUState.SINGLE_STEP))) {
      cpuState = CPUState.STOPPED;
      addDebuggerResponse("T05"); // Signal 5 is SIGTRAP
      return;
    }
  }

  public void stop() {
    cpuState = CPUState.STOPPED;
  }

  public boolean isTimerInterruptPending() {
    MSTATUS_CSR mstatus = (MSTATUS_CSR) MMCSR.getCSR(this, MMCSR.MSTATUS);
    MIP_CSR mip = (MIP_CSR) MMCSR.getCSR(this, MIP);
    MIE_CSR mie = (MIE_CSR) MMCSR.getCSR(this, MIE);

    boolean machineInterruptsEnabled = (mstatus.MIE.get() == 1);
    boolean machineTimerInterruptPending = ( (mip.read() & 0x80) == 0x80);
    boolean machineTimerInterruptsEnabled = ( (mie.read() & 0x80) == 0x80);
    return (machineInterruptsEnabled  && machineTimerInterruptsEnabled && machineTimerInterruptPending);
  }

  public boolean isExternalInterruptPending() {
    MSTATUS_CSR mstatus = (MSTATUS_CSR) MMCSR.getCSR(this, MMCSR.MSTATUS);
    MIP_CSR mip = (MIP_CSR) MMCSR.getCSR(this, MIP);
    MIE_CSR mie = (MIE_CSR) MMCSR.getCSR(this, MIE);

    boolean machineInterruptsEnabled = (mstatus.MIE.get() == 1);
    boolean machineExternalInterruptPending = ((mip.read() & 0x800) == 0x800);
    boolean machineExternalInterruptsEnabled = ((mie.read() & 0x800) == 0x800);
    return (machineInterruptsEnabled && machineExternalInterruptsEnabled && machineExternalInterruptPending);
  }

  // Handling debugger requests
  public boolean setDebuggerRequest(DebuggerRequest r) {
    synchronized (this) {
      if (debuggerRequest != null) {
        return false;
      } else {
        debuggerRequest = r;
        return true;
      }
    }
  }

  public void setGDBServer(GDBServer server) {
    this.gdbServer = server;
  }

  public void addDebuggerResponse(String response) {
    if (gdbServer != null) {
      gdbServer.setDebuggerResponse(response);
    }
  }

  /** getters and setters*/
  public Value getAddress() { return address; }
  public Value getMemRead() { return memRead; }
  public Value getMemWrite() { return memWrite;  }
  public ProgramCounter getPC() { return pc; }
  public CPUState getCpuState() { return cpuState; }
  public InstructionRegister getIR() { return ir; }
  public long getX(int index) { return x.get(index); }
  public void setX(int index, long value) { x.set(index,value); }
  public boolean getAddressing() { return addressing; }
  public long getCSRValue(int csr) {return this.csr.read(this, csr);}
  public CSR getCSR(int csr) { return this.csr.get(csr); }
  public IntegerRegisters getIntegerRegisters() {return this.x;}

  public int getOutputDataWidth() {
    return outputDataWidth;
  }
  // build output data value based on data width and the address
  public Value getOutputData() {
    return switch (outputDataWidth) {
      case 1 -> getInvertedOutputDataMask().not().controls(
              Value.createKnown(32, (outputData & 0xff) << ((getAddress().toLongValue() & 0x3) * 8)));
      case 2 -> getInvertedOutputDataMask().not().controls(
              Value.createKnown(32, (outputData & 0xffff) << ((getAddress().toLongValue() & 0x3) * 8)));
      default -> Value.createKnown(32, outputData);
    };
  }

  public Value getInvertedOutputDataMask() {
    return switch (outputDataWidth) {
      case 1 -> byteMasks[(int) getAddress().toLongValue() & 0x3];
      case 2 -> hwMasks[((int) getAddress().toLongValue() & 0x3) >> 1];
      case 4 -> ALL0s;
      default -> ALL1s;
    };
  }

  public void setOutputData(long value) { outputData = value; }
  public void setOutputDataWidth(int value) { outputDataWidth = value; }
  public void setCpuState(CPUState newCpuState) { cpuState = newCpuState; }
  public void setFetching(boolean value) { fetching = value; }
  public void setAddressing(boolean value) { addressing = value; }
  public void setAddress(Value newAddress) { address = newAddress; }
  public void setMemRead(Value value) { memRead = value; }
  public void setMemWrite(Value value) { memWrite = value; }
  public void setCSR(int csr, long value) { this.csr.write(this, csr, value); }

  // Breakpoint handling
  public void setBreakpoint(long address) {
    breakpoints.put(address, true);
  }

  public void removeBreakpoint(long address) {
    breakpoints.remove(address);
  }

  public void setBreakpointsEnabled(boolean enabled) {
    this.breakpointsEnabled = enabled;
  }

  public boolean isBreakpointHit() {
    return breakpointsEnabled && breakpoints.containsKey(pc.get());
  }

}