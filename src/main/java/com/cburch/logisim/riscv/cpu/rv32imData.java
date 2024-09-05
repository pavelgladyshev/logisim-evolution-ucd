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
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.riscv.cpu.csrs.*;
import com.cburch.logisim.riscv.cpu.gdb.MemoryAccessRequest;
import com.cburch.logisim.riscv.cpu.gdb.Breakpoint;
import java.util.ArrayList;
import java.util.List;

import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.MIP;
import static com.cburch.logisim.riscv.cpu.csrs.MMCSR.MIE;

/** Represents the state of a cpu. */
public class rv32imData implements InstanceData, Cloneable {

  /** The last values observed. */
  private Value lastClock;
  private long lastDataIn;
  private long lastAddress;

  /** Output values */
  static final Value HiZ32 = Value.createUnknown(BitWidth.create(32));
  private Value address;          // value to be placed on the address bus;
  private Value outputData;       // value to be placed on data bus
  private int outputDataWidth;    // width of the data to be written in bytes (1,2,or 4)
  private Value memRead;
  private Value memWrite;
  private Value isSync;

  /** Boolean flags */
  private boolean fetching;
  private boolean addressing;
  private boolean intermixFlag;
  private boolean pressedContinue;

  /** Registers */
  private final ProgramCounter pc;
  private final InstructionRegister ir;
  private final IntegerRegisters x;

  /** CSR registers */
  private final ControlAndStatusRegisters csr;

  /** Enum representing CPU states */
  private CPUState cpuState;

  public enum CPUState {
    OPERATIONAL,
    HALTED
  }


  /** GDB server */
  private GDBServer server;
  private GDB_SERVICE service;

  public enum GDB_SERVICE {
    NONE,
    STEPPING,
    MEMORY_ACCESS
  }

  private List<Breakpoint> breakpoints;

  public rv32imData(Value lastClock, long resetAddress){
    // initial values for registers
    this.lastClock = lastClock;
    this.pc = new ProgramCounter(resetAddress);
    this.ir = new InstructionRegister(0x13); // Initial value 0x13 is opcode for addi x0,x0,0 (nop)
    this.x = new IntegerRegisters();
    this.csr = new ControlAndStatusRegisters();
    this.cpuState = CPUState.OPERATIONAL;
    this.intermixFlag = false;
    this.pressedContinue = false;
    this.breakpoints = new ArrayList<>();
    // In the first clock cycle we are fetching the first instruction
    fetchNextInstruction();
  }

  /** Constructs a state with the given values. */
  public rv32imData(Value lastClock, long resetAddress, int port, CPUState state, Boolean runGDB) {
    // initial values for registers
    this.lastClock = lastClock;
    this.pc = new ProgramCounter(resetAddress);
    this.ir = new InstructionRegister(0x13); // Initial value 0x13 is opcode for addi x0,x0,0 (nop)
    this.x = new IntegerRegisters();
    this.csr = new ControlAndStatusRegisters();
    this.cpuState = state;
    this.intermixFlag = false;
    this.pressedContinue = false;
    this.service = GDB_SERVICE.NONE;
    if(runGDB) {
      this.server = new GDBServer(port, this);
      this.cpuState = CPUState.HALTED;
    }
    this.breakpoints = new ArrayList<>();
    // In the first clock cycle we are fetching the first instruction
    fetchNextInstruction();
  }

  /**
   * Set up outputs to fetch next instruction
   */
   public void fetchNextInstruction()
   {
       fetching = true;
       addressing = false;

       // Values for outputs fetching instruction
       address = Value.createKnown(32, pc.get());
       outputData = HiZ32;     // The output data bus is in High Z
       outputDataWidth = 4;    // all 4 bytes of the output
       memRead = Value.TRUE;   // MemRead active
       memWrite = Value.FALSE; // MemWrite not active
       isSync = Value.TRUE;
   }

  /**
   * Retrieves the state associated with this counter in the circuit state, generating the state if
   * necessary.
   */
  public static rv32imData get(InstanceState state) {
    rv32imData ret = (rv32imData) state.getData();
    if (ret == null) {
      // If it doesn't yet exist, then we'll set it up with our default
      // values and put it into the circuit state so it can be retrieved
      // in future propagations.
      CPUState initialCPUState = state.getAttributeValue(rv32im.ATTR_CPU_STATE).getValue().equals("Halted") ? CPUState.HALTED : CPUState.OPERATIONAL;
      ret = new rv32imData(null, state.getAttributeValue(rv32im.ATTR_RESET_ADDR),
              state.getAttributeValue(rv32im.ATTR_TCP_PORT), initialCPUState, state.getAttributeValue(rv32im.ATTR_GDB_SERVER_RUNNING));
      state.setData(ret);
    }
    return ret;
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

  /* GDB Breakpoints handling*/
  public boolean addBreakpoint(Breakpoint.Type breakpointType, long address, int kind) {
    Breakpoint breakpoint = new Breakpoint(breakpointType, address, kind);
    breakpoints.add(breakpoint);
    return true;
  }

  public boolean removeBreakpoint(Breakpoint.Type breakpointType, long address, int kind) {
    Breakpoint.Type type = breakpointType;
    return breakpoints.removeIf(bp -> bp.getType() == type && bp.getAddress() == address && bp.getKind() == kind);
  }

  public boolean checkBreakpoint(long address) {
    for (Breakpoint bp : breakpoints) {
      if (bp.getAddress() == address) {
        System.out.println("Breakpoint hit at address: " + address);
        return true;
      }
    }
    return false;
  }

  /* GDB memory access handling */
  public void processMemoryAccessRequest(MemoryAccessRequest request, long dataIn){
    //check for failure to read memory?   fail request
    //check for failure to write memory?  fail request
    switch(request.getType()){
        case MEMREAD -> {
          if(!addressing){
            System.out.println("addressing at : 0x"+Long.toHexString(request.getNextAddress().toLongValue()));
            LoadInstruction.performAddressing(this, request.getNextAddress());
          }
          else {
            System.out.println("data in : 0x"+Long.toHexString(dataIn));
            System.out.println("current address : 0x"+Long.toHexString(request.getNextAddress().toLongValue()));
            long nextByte =  LoadInstruction.getUnsignedDataByte(dataIn, request.getNextAddress().toLongValue());
            request.getDataBuffer().append(String.format("%02X",nextByte));
            request.incrementAccessed();
            fetchNextInstruction();
          }
        }
        case MEMWRITE -> {
          long nextDataByte = request.getNextDataByte();
          System.out.println("Written : " + nextDataByte);
          StoreInstruction.performAddressing(this, nextDataByte, request.getNextAddress());
          intermixFlag = (addressing);
          request.incrementAccessed();
        }
    }
  }

  /** update CPU state (execute) */
  public boolean update(long dataIn) {
      boolean instructionCompleted = false;

    lastDataIn = dataIn;
    lastAddress = address.toLongValue();

      if(fetching) {
        ir.set(dataIn);
      }

      if (!isHalted()) {
        // Check for timer interrupts
        if (isTimerInterruptPending()) {
          TrapHandler.handle(this, MCAUSE_CSR.TRAP_CAUSE.MACHINE_TIMER_INTERRUPT);
          fetchNextInstruction();
        }
        // Check for external interrupts
        else if (isExternalInterruptPending()) {
          TrapHandler.handle(this, MCAUSE_CSR.TRAP_CAUSE.MACHINE_EXTERNAL_INTERRUPT);
          fetchNextInstruction();
        }
        else {
          instructionCompleted = handleNextInstruction(dataIn);
        }
      }
      return instructionCompleted;
    }

  // Boolean flag indicates whether an instruction was completed in this cycle.
  public boolean handleNextInstruction(long dataIn){
    boolean instructionCompleted = true;
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
          instructionCompleted = false;
        } else {
          LoadInstruction.latch(this, dataIn);
          fetchNextInstruction();
        }
        break;
      case 0x23:  // storing instruction (S-type)
        StoreInstruction.performAddressing(this);
        intermixFlag = (addressing);
        instructionCompleted = false;
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
    return instructionCompleted;
  }

  public void stopGDBServer() {
    if(isGDBRunning()) server.terminate();
  }

  public void halt() {
    isSync = Value.FALSE;
    cpuState = CPUState.HALTED;
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

  public Boolean isHalted() {
    return getCpuState() == CPUState.HALTED;
  }

  public boolean isGDBRequestPending() {
    if(server == null) return false;
    return server.isRequestPending();
  }

  public boolean isGDBRunning(){
    return (server != null);
  }

  /** getters and setters*/
  public long getLastDataIn() { return lastDataIn; }
  public long getLastAddress() { return lastAddress; }
  public Value getAddress() { return address; }
  public Value getOutputData() { return outputData; }
  public int getOutputDataWidth() { return outputDataWidth; }
  public Value getMemRead() { return memRead; }
  public Value getMemWrite() { return memWrite;  }
  public ProgramCounter getPC() { return pc; }
  public CPUState getCpuState() { return cpuState; }
  public InstructionRegister getIR() { return ir; }
  public Value getIsSync() { return isSync; }
  public long getX(int index) { return x.get(index); }
  public void setX(int index, long value) { x.set(index,value); }
  public boolean getAddressing() { return addressing; }
  public boolean getIntermixFlag() { return intermixFlag; }
  public boolean getPressedContinue() { return pressedContinue; }
  public long getCSRValue(int csr) {return this.csr.read(this, csr);}
  public CSR getCSR(int csr) { return this.csr.get(csr); }
  public GDBServer getServer() { return server; }
  public GDB_SERVICE getService() { return service; }

  public List<Breakpoint> getBreakpoints() { return breakpoints; }

  public void setLastDataIn(long value) { lastDataIn = value; }
  public void setLastAddress(long value) { lastAddress = value; }
  public void setOutputData(Value value) { outputData = value; }
  public void setCpuState(CPUState newCpuState) { cpuState = newCpuState; }
  public void setFetching(boolean value) { fetching = value; }
  public void setAddressing(boolean value) { addressing = value; }
  public void setAddress(Value newAddress) { address = newAddress; }
  public void setOutputDataWidth(int value) { outputDataWidth = value; }
  public void setMemRead(Value value) { memRead = value; }
  public void setMemWrite(Value value) { memWrite = value; }
  public void setIsSync(Value value) { isSync = value; }
  public void setIntermixFlag(boolean value) { intermixFlag = value; }
  public void setPressedContinue(boolean value) { pressedContinue = value; }
  public void setCSR(int csr, long value) { this.csr.write(this, csr, value); }
  public void setService(GDB_SERVICE service) {this.service = service;}
}
