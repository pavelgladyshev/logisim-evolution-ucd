/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceState;
import static com.cburch.logisim.riscv.MMCSR.MIP;
import static com.cburch.logisim.riscv.MMCSR.MIE;

/** Represents the state of a cpu. */
class rv32imData implements InstanceData, Cloneable {

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

  // More To Do

  /** Constructs a state with the given values. */
  public rv32imData(Value lastClock, long resetAddress) {

    // initial values for registers
    this.lastClock = lastClock;
    this.pc = new ProgramCounter(resetAddress);
    this.ir = new InstructionRegister(0x13); // Initial value 0x13 is opcode for addi x0,x0,0 (nop)
    this.x = new IntegerRegisters();
    this.csr = new ControlAndStatusRegisters();
    this.cpuState = CPUState.OPERATIONAL;
    this.intermixFlag = false;
    this.pressedContinue = false;

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
     address = Value.createKnown(32,pc.get());
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
      ret = new rv32imData(null, state.getAttributeValue(rv32im.ATTR_RESET_ADDR));
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

  /** update CPU state (execute) */
  public void update(long dataIn) {

    if (isInterruptPending()) {
          TrapHandler.handle(this, MCAUSE_CSR.TRAP_CAUSE.MACHINE_TIMER_INTERRUPT);
          fetchNextInstruction();
          return;
    }

    if (fetching) { lastDataIn = dataIn; lastAddress = address.toLongValue(); ir.set(dataIn); }

    switch(ir.opcode()) {
      case 0x13:  // I-type arithmetic instruction
        ArithmeticInstruction.executeImmediate(this);
        pc.increment();
        fetchNextInstruction();
        break;
      case 0x33:  // R-type arithmetic & multiplication and division instructions
        ArithmeticInstruction.executeRegister(this);
        pc.increment();
        fetchNextInstruction();
        break;
      case 0x03:  // load instruction (I-type)
        if(!addressing) {
          LoadInstruction.performAddressing(this);
        } else {
          LoadInstruction.latch(this, dataIn);
          pc.increment();
          fetchNextInstruction();
        }
        break;
      case 0x23:  // storing instruction (S-type)
        StoreInstruction.performAddressing(this);
        intermixFlag = (cpuState == CPUState.OPERATIONAL);  // WE ARE MIXING DATA ONLY WHEN INSTRUCTION DOESN'T HALT CPU!
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
        if(ir.func3() != 0x0) pc.increment();
        fetchNextInstruction();

        break;
      default:  // Unknown instruction
        TrapHandler.throwIllegalInstructionException(this);
    }
  }

  public void halt() {
    isSync = Value.FALSE;
    cpuState = CPUState.HALTED;
  }

  public void skipInstruction() {
    pc.increment();
    fetchNextInstruction();
  }

  public boolean isInterruptPending() {
    MSTATUS_CSR mstatus = (MSTATUS_CSR) MMCSR.getCSR(this, MMCSR.MSTATUS);
    MIP_CSR mip = (MIP_CSR) MMCSR.getCSR(this, MIP);
    MIE_CSR mie = (MIE_CSR) MMCSR.getCSR(this, MIE);

    boolean machineInterruptsEnabled = (mstatus.MIE.get() == 1);
    boolean machineTimerInterruptPending = ( (mip.read() & 0x80) == 0x80);
    boolean machineTimerInterruptsEnabled = ( (mie.read() & 0x80) == 0x80);
    return (machineInterruptsEnabled  && machineTimerInterruptsEnabled && machineTimerInterruptPending);
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
}
