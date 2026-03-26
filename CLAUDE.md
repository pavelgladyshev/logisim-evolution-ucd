# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build and run the application
./gradlew run

# Build fat JAR (creates build/libs/logisim-evolution-ucd-<version>-all.jar)
./gradlew shadowJar

# Run the built JAR
java -jar build/libs/logisim-evolution-ucd-4.1.1-all.jar

# Run tests (JUnit 5)
./gradlew test

# Run a single test class
./gradlew test --tests "com.cburch.logisim.riscv.BranchingTest"

# Run a single test method
./gradlew test --tests "com.cburch.logisim.riscv.BranchingTest.testBEQ"

# Run nested test class (use $ for inner classes)
./gradlew test --tests "com.cburch.logisim.riscv.Sv32Test\$PermissionTests"

# Code style check (Google Java Style via Checkstyle 11.0.0)
./gradlew checkstyleMain
./gradlew checkstyleTest

# Generate source files (BuildInfo.java, VhdlSyntax.java)
./gradlew genFiles

# Create platform-specific installer (output in build/dist)
./gradlew createAll

# List all available tasks
./gradlew tasks --all
```

## Project Info

- **Name:** `logisim-evolution-ucd` (group: `com.cburch`)
- **Version:** `4.1.1` (set in `gradle.properties`)
- **Java:** 21 (sourceCompatibility and targetCompatibility)
- **Build:** Gradle with Kotlin DSL (`build.gradle.kts`)
- **Shadow JAR output:** `build/libs/logisim-evolution-ucd-4.1.1-all.jar`

## Architecture Overview

Logisim-evolution is a Java 21 Swing application for digital logic circuit design and simulation. Entry point: `com.cburch.logisim.Main`.

### Core Packages

- **`com.cburch.logisim.circuit`** - Circuit model and simulation engine. `Circuit` holds components/wires, `CircuitState` maintains execution state, `Simulator` drives propagation.

- **`com.cburch.logisim.comp` / `instance`** - Component system. `ComponentFactory` defines component types, `InstanceFactory`/`Instance` is the modern implementation pattern with `InstanceState` for runtime state.

- **`com.cburch.logisim.std.*`** - Standard component libraries (gates, arithmetic, memory, I/O, TTL ICs, plexers, wiring).

- **`com.cburch.logisim.gui`** - Swing UI. `Frame` is the main window with canvas-based circuit editing. Uses FlatLAF for modern look-and-feel.

- **`com.cburch.logisim.proj` / `file`** - Project management and XML persistence. `Project` is the main container, `LogisimFile` handles serialization.

- **`com.cburch.logisim.data`** - Core data types. `Value` represents 4-state logic (0, 1, X, Z), `BitWidth` for bus widths, `Attribute`/`AttributeSet` for component properties.

### RISC-V Implementation (UCD Fork)

This fork includes a custom RISC-V RV32IM processor implementation with SV32 virtual memory:

- **`com.cburch.logisim.riscv.cpu`** - Core processor
  - `rv32im.java` - Component factory (14 ports: clock, reset, data bus, address bus, mem read/write, timer/external interrupt inputs, byte enables, bus request/ack)
  - `rv32imData.java` - Complete CPU state (registers, CSRs, TLB, cache, privilege mode, GDB server integration). Key methods: `isTranslationEnabled()` (fetch), `isTranslationEnabledForLoadStore()` (MPRV-aware), `getEffectivePrivilegeForLoadStore()`, `checkTlbPermissions()` (fetch), `checkTlbPermissionsForLoadStore()` (MPRV-aware)
  - `ArithmeticInstruction` - I-type, R-type, and M-extension (MUL/MULH/MULHSU/MULHU/DIV/DIVU/REM/REMU). Shift instructions cast to `int` for 32-bit semantics; logical shifts zero-extend result with `& 0xFFFFFFFFL`
  - `BranchInstruction` - BEQ, BNE, BLT, BGE, BLTU, BGEU
  - `LoadInstruction`, `StoreInstruction` - Memory operations with virtual address translation
  - `JumpAndLink` - JAL, JALR
  - `SystemInstruction` - ECALL, EBREAK, MRET (clears MPRV when MPP!=M per §3.1.6.3), SRET, SFENCE.VMA, all Zicsr instructions (CSRRW/S/C/WI/SI/CI)
  - `TrapHandler` - Exception/interrupt dispatch with M/S-mode delegation via MEDELEG/MIDELEG
  - `IntegerRegisters` - x0-x31 register file
  - `ProgramCounter`, `InstructionRegister` - PC and instruction decoding

- **`com.cburch.logisim.riscv.cpu` (virtual memory subsystem)**
  - `TranslationLookasideBuffer` - 128-entry fully associative TLB with LRU, ASID isolation, global page support, 4KB and 4MB megapages, fast-path single-entry cache, direct-mapped 256-entry accelerator. All address inputs masked to 32 bits (`& 0xFFFFFFFFL`) to prevent sign-extension bugs
  - `PageTableWalker` - SV32 two-level page table walk state machine (L1→L0), PTE validation (V, reserved W+!R, megapage alignment), delegates permission checks to `PermissionCheck` (MPRV-aware for load/store walks)
  - `PermissionCheck` - Static permission validation per Spec §4.3.2 step 6: A/D bit enforcement, U-bit privilege isolation, MXR (Make eXecutable Readable), SUM (Supervisor User Memory access), MPRV (Modify PRiVilege for M-mode load/store)
  - `MemoryCache` - Instruction cache

- **`com.cburch.logisim.riscv.cpu`** (CSR access)
  - `ControlAndStatusRegisters` - CSR file (4096 entries), privilege-checked access via `checkAccess()` (privilege level from bits [9:8], read-only from bits [11:10])

- **`com.cburch.logisim.riscv.cpu.csrs`** - Control and Status Registers
  - `MSTATUS_CSR` - Machine status (MIE, MPIE, MPP, SIE, SPIE, SPP, MPRV, SUM, MXR)
  - `SSTATUS_CSR` - Supervisor view of MSTATUS (masked proxy: bits 1, 5, 8, 18, 19)
  - `SATP_CSR` - SV32 control (MODE bit 31, ASID bits 30:22, PPN bits 21:0)
  - `MCAUSE_CSR` / `SCAUSE_CSR` - Trap cause (interrupt bit + exception code)
  - `MTVEC_CSR` / `STVEC_CSR` - Trap vector (direct and vectored modes)
  - `MIE_CSR`, `MIP_CSR`, `SIE_CSR`, `SIP_CSR` - Interrupt enable/pending
  - `MEDELEG_CSR`, `MIDELEG_CSR` - Exception/interrupt delegation to S-mode
  - `PRIVILEGE_MODE` - M (3), S (1), U (0) privilege levels
  - `MMCSR` enum - All 111 machine-mode CSR addresses
  - `SCSR` enum - 12 supervisor-mode CSR addresses
  - `UCSR` enum - 72 user-mode CSR addresses (counters, perf monitoring)

- **`com.cburch.logisim.riscv.cpu.gdb`** - GDB remote debugging server
  - TCP-based GDB protocol on configurable port (default 3333)
  - Breakpoints, single-step, continue commands
  - Register and memory read/write operations
  - Runs on separate thread with synchronous communication to CPU simulation

- **`com.cburch.logisim.riscv.timer`** - RISC-V mtime/mtimecmp timer with interrupt generation

- **`com.cburch.logisim.riscv.plic`** - Platform-Level Interrupt Controller (priority, threshold, claim/complete)

- **`com.cburch.logisim.riscv.videoram`** - Monochrome display memory

- **`com.cburch.logisim.riscv.blockstorage`** - Persistent block storage device

### SoC Framework

- **`com.cburch.logisim.soc`** - Alternative SoC implementation with bus architecture (~87 files)
  - `bus` - Bus arbiter, transaction handling, memory mapping
  - `memory` - SoC addressable memory
  - `pio` - General-purpose I/O
  - `vga` - VGA display controller
  - `jtaguart` - JTAG UART controller
  - `rv32im` - SoC-integrated RISC-V variant with assembler and syntax highlighting
  - `nios2` - NIOS2 processor variant
  - `file` - ELF binary loader (headers, sections, symbols)
  - `util` - Assembler framework and execution interfaces

### FPGA & HDL

- **`com.cburch.logisim.fpga`** - FPGA synthesis, HDL generation (VHDL/Verilog), board support
- **`com.cburch.logisim.vhdl`** - VHDL entity integration

### Generated Sources

Build generates files in `build/generated/`:
- `BuildInfo.java` - Version, git info, build timestamp
- `VhdlSyntax.java` - Generated from JFlex

## Code Style

Uses Google Java Style with Checkstyle 11.0.0 and suppressions in `checkstyle-suppressions.xml`. Run `./gradlew checkstyleMain` to verify.

## Testing

Tests are in `src/test/java/com/cburch/logisim/riscv/`. JUnit 5 with Mockito.

### RISC-V Test Files (18 classes)

**Instruction tests:**
- `ArithmeticTypeITest` - Immediate arithmetic (ADDI, SLTI, XORI, ORI, ANDI, SLLI, SRLI, SRAI)
- `ArithmeticTypeRTest` - Register arithmetic (ADD, SUB, SLL, SLT, SLTU, XOR, SRL, SRA, OR, AND)
- `ArithmeticTypeRMulAndDivTest` - M-extension (MUL, MULH, MULHSU, MULHU, DIV, DIVU, REM, REMU)
- `ArithmeticTypeUTest` - Upper immediate (LUI, AUIPC)
- `BranchingTest` - All branch types
- `JumpAndLinkTest` - JAL/JALR

**System and control tests:**
- `SystemInstructionTest` - ECALL, EBREAK, MRET, SRET
- `CSRAccessTest` - CSR read/write, privilege checks, write suppression
- `ExceptionTest` - Exception generation and trap handling

**Memory tests:**
- `LoadDataCacheTest`, `StoreDataCacheTest` - Load/store with caching
- `MemoryCacheTest` - Cache hit/miss behavior

**Virtual memory tests (SV32):**
- `PageTableWalkerTest` - TLB operations, SATP CSR fields
- `PermissionCheckTest` - A/D bits, U-bit, MXR, R/W/X enforcement
- `Sv32Test` - Comprehensive SV32 suite (102 tests in 9 nested classes): SATP, MSTATUS/SSTATUS SUM+MXR, permissions, TLB, translation enable, SFENCE.VMA, SRET/MRET, page fault traps, edge cases
- `MprvTest` - MPRV functionality (37 tests in 6 nested classes): bitfield, effective privilege, translation enable for load/store vs fetch, permission checks with MPRV, MRET clearing, OS scenarios (copy_from_user)
- `TlbPerformanceTest` - TLB hit rates, fast-path cache, LRU eviction, statistics

**Integration tests:**
- `rv32imDataTest` - CPU state lifecycle

### Running Tests

```bash
# All tests
./gradlew test

# All RISC-V virtual memory tests
./gradlew test --tests "com.cburch.logisim.riscv.Sv32Test" \
               --tests "com.cburch.logisim.riscv.MprvTest" \
               --tests "com.cburch.logisim.riscv.PageTableWalkerTest" \
               --tests "com.cburch.logisim.riscv.PermissionCheckTest" \
               --tests "com.cburch.logisim.riscv.TlbPerformanceTest"

# All instruction tests
./gradlew test --tests "com.cburch.logisim.riscv.Arithmetic*" \
               --tests "com.cburch.logisim.riscv.BranchingTest" \
               --tests "com.cburch.logisim.riscv.JumpAndLinkTest"
```

### Key Test Patterns

Tests use `rv32imData` directly without the full simulation harness:
```java
rv32imData cpu = new rv32imData(Value.FALSE, resetAddr, tcpPort, false, false,
    rv32imData.CPUState.RUNNING, null);
cpu.update(encodedInstruction, timerIRQ, extIRQ, waitRequest);
// Signature: update(dataIn, timerInterruptRequest, externalInterruptRequest, waitRequest)
// First call: dataIn=instruction (latched during fetch phase), then executed
```

Permission checks are testable via the static `PermissionCheck.check()` method without needing CPU state.
