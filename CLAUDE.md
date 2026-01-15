# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build and run the application
./gradlew run

# Build fat JAR (creates build/libs/logisim-evolution-<version>-all.jar)
./gradlew shadowJar

# Run the built JAR
java -jar build/libs/logisim-evolution-3.9.0dev-all.jar

# Run tests (JUnit 5)
./gradlew test

# Run a single test class
./gradlew test --tests "com.cburch.logisim.riscv.BranchingTest"

# Run a single test method
./gradlew test --tests "com.cburch.logisim.riscv.BranchingTest.testBEQ"

# Code style check (Google Java Style via Checkstyle)
./gradlew checkstyleMain
./gradlew checkstyleTest

# Generate source files (BuildInfo.java, VhdlSyntax.java)
./gradlew genFiles

# Create platform-specific installer (output in build/dist)
./gradlew createAll

# List all available tasks
./gradlew tasks --all
```

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

This fork includes a custom RISC-V RV32IM processor implementation:

- **`com.cburch.logisim.riscv`** - Main RISC-V package
  - `rv32im` - Core processor component
  - `IntegerRegisters`, `ControlAndStatusRegisters` - Register files
  - `ArithmeticInstruction`, `BranchInstruction`, `LoadInstruction`, `StoreInstruction`, `JumpAndLink`, `SystemInstruction` - Instruction implementations
  - `TrapHandler` - Exception handling
  - `Timer`, `PLIC` - Peripheral components
  - `MonochromeVideoram` - Display memory
  - `MemoryCache` - Data cache implementation

- **`com.cburch.logisim.riscv.gdb`** - GDB remote debugging server
  - TCP-based GDB protocol support
  - Breakpoints, single-step, continue commands
  - Memory read/write operations
  - Runs on separate thread with synchronous communication to CPU simulation

### SoC Framework

- **`com.cburch.logisim.soc`** - Alternative SoC implementation with bus architecture
  - `SocBus`, `SocMemory`, `SocPio`, `SocVga`, `JtagUart`
  - `soc.rv32im` - SoC-integrated RISC-V variant

### FPGA & HDL

- **`com.cburch.logisim.fpga`** - FPGA synthesis, HDL generation (VHDL/Verilog), board support
- **`com.cburch.logisim.vhdl`** - VHDL entity integration

### Generated Sources

Build generates files in `build/generated/`:
- `BuildInfo.java` - Version, git info, build timestamp
- `VhdlSyntax.java` - Generated from JFlex

## Code Style

Uses Google Java Style with suppressions in `checkstyle-suppressions.xml`. Run `./gradlew checkstyleMain` to verify.

## Testing

Tests are in `src/test/java/`. The `riscv` package has extensive instruction tests (arithmetic, branching, jumps, exceptions, memory cache). Use JUnit 5 with Mockito for mocking.
