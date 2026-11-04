package com.cburch.logisim.riscv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.rv32imData;
import com.cburch.logisim.riscv.cpu.csrs.MMCSR;
import com.cburch.logisim.riscv.cpu.csrs.PRIVILEGE_MODE;
import com.cburch.logisim.riscv.cpu.csrs.SCSR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The RV32IM CPU where it used to differ from the RISC-V specs: 32-bit registers, misaligned loads and stores,
 * the order of a load's checks, the privilege of mret, sret and sfence.vma, wfi and uret, interrupts between
 * instructions only and with their delegation, traps that go on with the handler, fence, jalr and the decoding of
 * ecall. Each update() is a clock tick; its dataIn is the word the bus gives (the instruction in a fetch).
 */
public class RiscvStandardTest {

  private static final long RESET = 0x400000;
  private static final long HANDLER = 0x1000;
  private static final long NOP = 0x13;
  private rv32imData cpu;

  @BeforeEach
  void setup() {
    cpu = new rv32imData(Value.FALSE, RESET, 1234, false, false, rv32imData.CPUState.RUNNING, null);
    cpu.setCSR(MMCSR.MTVEC.getAddress(), HANDLER);
  }

  private long csr(MMCSR register) {
    return cpu.getCSRValue(register.getAddress()) & 0xFFFFFFFFL;
  }

  private long busAddress() {
    return cpu.getAddress().toLongValue() & 0xFFFFFFFFL;
  }

  /** The trap went to the M-mode handler with this cause, and the CPU fetches the handler next. */
  private void assertTrap(long cause) {
    assertEquals(HANDLER, cpu.getPC().get(), "pc at the handler");
    assertEquals(cause, csr(MMCSR.MCAUSE), "mcause");
    assertEquals(HANDLER, busAddress(), "the handler's fetch on the bus");
    assertEquals(Value.TRUE, cpu.getMemRead());
  }

  @Test
  void registers_hold32Bits() {
    cpu.setX(6, -1);
    cpu.update(0x00035293, 0, 0, 0);   // srli x5, x6, 0: 0xffffffff
    cpu.update(0x0002a393, 0, 0, 0);   // slti x7, x5, 0: -1 < 0
    assertEquals(1, cpu.getX(7));
    cpu.setX(6, 0x7fffffff);
    cpu.update(0x00130293, 0, 0, 0);   // addi x5, x6, 1: 0x80000000
    cpu.update(0x0002a393, 0, 0, 0);   // slti x7, x5, 0: -2^31 < 0
    assertEquals(1, cpu.getX(7));
    assertEquals(-0x80000000L, cpu.getX(5));
  }

  @Test
  void bltu_comparesUnsigned32Bits() {
    cpu.setX(5, 0xffffffffL);   // the same 32 bits as -1
    cpu.setX(6, -1);
    final long pc = cpu.getPC().get();
    cpu.update(0x0062e463, 0, 0, 0);   // bltu x5, x6, +8: equal, not taken
    assertEquals(pc + 4, cpu.getPC().get());
  }

  @Test
  void misalignedLoad_isLoadAddressMisaligned() {
    cpu.setX(6, 0x100);
    cpu.setX(5, 77);
    cpu.update(0x00232283, 0, 0, 0);   // lw x5, 2(x6)
    assertTrap(4);
    assertEquals(0x102, csr(MMCSR.MTVAL));
    assertEquals(77, cpu.getX(5));
    cpu.update(NOP, 0, 0, 0);          // the handler's first instruction runs
    assertEquals(HANDLER + 4, cpu.getPC().get());
  }

  @Test
  void misalignedStore_isStoreAddressMisaligned() {
    cpu.setX(6, 0x100);
    cpu.update(0x00532123, 0, 0, 0);   // sw x5, 2(x6)
    assertTrap(6);
    assertEquals(0x102, csr(MMCSR.MTVAL));
    assertEquals(Value.FALSE, cpu.getMemWrite());
  }

  @Test
  void loadWithAnIllegalFunct3_trapsWithoutABusCycle() {
    cpu.update(0x00033283, 0, 0, 0);   // load with funct3 3
    assertTrap(2);
  }

  @Test
  void unknownOpcode_trapsAndGoesOnWithTheHandler() {
    cpu.update(0x0000007f, 0, 0, 0);
    assertTrap(2);
    cpu.update(NOP, 0, 0, 0);
    assertEquals(HANDLER + 4, cpu.getPC().get());
  }

  @Test
  void mret_isMachineModeOnly() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    cpu.update(0x30200073, 0, 0, 0);   // mret in S-mode
    assertTrap(2);
    assertEquals(PRIVILEGE_MODE.MACHINE, cpu.getCurrentPrivilegeMode());
  }

  @Test
  void sretAndSfenceVma_areIllegalInUserMode() {
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.USER);
    cpu.update(0x10200073, 0, 0, 0);   // sret
    assertTrap(2);
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.USER);
    cpu.update(0x12000073, 0, 0, 0);   // sfence.vma x0, x0
    assertTrap(2);
  }

  @Test
  void wfi_goesOn_uret_isIllegal() {
    final long pc = cpu.getPC().get();
    cpu.update(0x10500073, 0, 0, 0);   // wfi
    assertEquals(pc + 4, cpu.getPC().get());
    cpu.update(0x00200073, 0, 0, 0);   // uret
    assertTrap(2);
  }

  @Test
  void ecall_needsItsWholeEncoding() {
    cpu.update(0x000000f3, 0, 0, 0);   // ecall's pattern with rd = x1
    assertTrap(2);
  }

  @Test
  void fence_andFenceI_goOn() {
    final long pc = cpu.getPC().get();
    cpu.update(0x0ff0000f, 0, 0, 0);   // fence iorw, iorw
    cpu.update(0x0000100f, 0, 0, 0);   // fence.i
    assertEquals(pc + 8, cpu.getPC().get());
  }

  @Test
  void jalr_takesItsTargetBeforeWritingRd() {
    cpu.setX(5, 0x2000);
    final long pc = cpu.getPC().get();
    cpu.update(0x000282e7, 0, 0, 0);   // jalr x5, 0(x5)
    assertEquals(0x2000, cpu.getPC().get());
    assertEquals(pc + 4, cpu.getX(5));
  }

  @Test
  void interrupt_waitsForAStoreToComplete() {
    cpu.setCSR(MMCSR.MSTATUS.getAddress(), 1L << 3);   // MIE
    cpu.setCSR(MMCSR.MIE.getAddress(), 1L << 7);       // MTIE
    cpu.setX(6, 0x100);
    cpu.update(0x00532023, 0, 0, 0);   // sw x5, 0(x6): its address and data on the bus
    assertEquals(Value.TRUE, cpu.getMemWrite());
    cpu.update(0, 1, 0, 0);            // the timer: the store completes first
    assertEquals(RESET + 4, cpu.getPC().get());
    assertEquals(0, csr(MMCSR.MCAUSE));
    cpu.update(NOP, 1, 0, 0);          // then the interrupt, before the next instruction
    assertTrap(0x80000007L);
    assertEquals(RESET + 4, csr(MMCSR.MEPC));
  }

  @Test
  void delegatedInterrupt_waitsForSie_inSupervisorMode() {
    cpu.setCSR(MMCSR.MIDELEG.getAddress(), 1L << 7);   // MTI to S-mode
    cpu.setCSR(MMCSR.MIE.getAddress(), 1L << 7);       // MTIE
    cpu.setCSR(SCSR.STVEC.getAddress(), 0x3000);
    cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
    final long pc = cpu.getPC().get();
    cpu.update(NOP, 1, 0, 0);          // the timer, but SIE 0: not taken
    assertEquals(pc + 4, cpu.getPC().get());
    cpu.setCSR(MMCSR.MSTATUS.getAddress(), 1L << 1);   // SIE
    cpu.update(NOP, 1, 0, 0);
    assertEquals(0x3000, cpu.getPC().get());
    assertEquals(0x80000007L, cpu.getCSRValue(SCSR.SCAUSE.getAddress()) & 0xFFFFFFFFL);
    assertEquals(PRIVILEGE_MODE.SUPERVISOR, cpu.getCurrentPrivilegeMode());
  }

  @Test
  void undelegatedSupervisorInterrupt_trapsToMachineMode() {
    cpu.setCSR(MMCSR.MSTATUS.getAddress(), 1L << 3);   // MIE
    cpu.setCSR(MMCSR.MIE.getAddress(), 1L << 1);       // SSIE
    cpu.setCSR(MMCSR.MIP.getAddress(), 1L << 1);       // SSIP, not delegated
    cpu.update(NOP, 0, 0, 0);
    assertTrap(0x80000001L);
  }
}
