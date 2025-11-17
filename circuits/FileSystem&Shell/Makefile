# =========================
# Build targets & toolchain
# =========================
TARGET ?= windows

KERNEL_DIR := kernel
USER_DIR   := user

ifeq ($(TARGET), logisim)
    CC       := riscv-none-embed-gcc
    OBJCOPY  := riscv-none-embed-objcopy
    OBJDUMP  := riscv-none-embed-objdump
    CFLAGS   := -march=rv32im -mstrict-align -nostdlib -g -DLOGISIM -I. -I$(KERNEL_DIR) -I$(USER_DIR)
    LDFLAGS  := -Wl,-T$(KERNEL_DIR)/logisim.lds,-T$(KERNEL_DIR)/kernel.ld -Wl,-Map=kernel.map -nostartfiles
    TARGET_EXT := elf
    PLATFORM_SOURCE := $(KERNEL_DIR)/platform_logisim.c
else
    CC       := x86_64-w64-mingw32-gcc
    CFLAGS   := -g -DWINDOWS -I. -I$(KERNEL_DIR) -I$(USER_DIR)
    LDFLAGS  :=
    TARGET_EXT := exe
    PLATFORM_SOURCE := $(KERNEL_DIR)/platform_windows.c
endif

# =========================
# Kernel sources & objects
# =========================
KERNEL_C_SOURCES := \
    $(KERNEL_DIR)/kernel.c \
    $(KERNEL_DIR)/console.c \
    $(KERNEL_DIR)/file_system.c \
    $(KERNEL_DIR)/syscall.c \
    $(KERNEL_DIR)/utils.c \
    $(KERNEL_DIR)/trap.c \
    $(PLATFORM_SOURCE)

# Note: assemble trap.S to trap_asm.o to avoid name clash with trap.c -> trap.o
KERNEL_ASM_SOURCES := \
    $(KERNEL_DIR)/crt0.S \
    $(KERNEL_DIR)/trap.S

KERNEL_C_OBJS   := $(KERNEL_C_SOURCES:.c=.o)
KERNEL_ASM_OBJS := $(KERNEL_DIR)/crt0.o $(KERNEL_DIR)/trap_asm.o
KERNEL_OBJECTS  := $(KERNEL_C_OBJS) $(KERNEL_ASM_OBJS)

# =========================
# User (shell) sources
# =========================

USER_CRT0      := $(USER_DIR)/crt0.S
USER_CRT0_OBJ  := $(USER_DIR)/crt0.o

# only link minimal, syscall-based userland (no kernel *.c here!)
USER_SHELL_SRCS := \
    $(USER_DIR)/main.c \
    $(USER_DIR)/shell.c \
    $(USER_DIR)/syscalls.c \
    $(KERNEL_DIR)/utils.c

USER_SHELL_OBJS := $(USER_SHELL_SRCS:.c=.o) $(USER_CRT0_OBJ)

# Setup tool (host utility that writes shell into disk image)
SETUP     := setup
SETUP_SRC := $(KERNEL_DIR)/setup.c

# =========================
# Top-level targets
# =========================
all: kernel.$(TARGET_EXT)

# -------- Kernel --------
kernel.$(TARGET_EXT): $(KERNEL_OBJECTS)
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $(KERNEL_OBJECTS)

kernel.bin: kernel.elf
	$(OBJCOPY) -O binary $< $@

kernel.asm: kernel.elf
	$(OBJDUMP) -d -S -g $< > $@

# Assemble kernel ASM
$(KERNEL_DIR)/crt0.o: $(KERNEL_DIR)/crt0.S
	$(CC) $(CFLAGS) -c -o $@ $<

$(KERNEL_DIR)/trap_asm.o: $(KERNEL_DIR)/trap.S
	$(CC) $(CFLAGS) -c -o $@ $<

# Generic C rule
%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<


# -------- User (logisim only) --------
ifeq ($(TARGET), logisim)

# Assemble user crt0
$(USER_CRT0_OBJ): $(USER_CRT0)
	$(CC) $(CFLAGS) -c -o $@ $<

# Link user shell (no libc)
$(USER_DIR)/shell.elf: $(USER_SHELL_OBJS)  $(USER_DIR)/logisim.lds
	$(CC) $(CFLAGS) -nostdlib -Wl,-T$(USER_DIR)/logisim.lds -o $@ $(USER_SHELL_OBJS)


$(USER_DIR)/shell.asm: $(USER_DIR)/shell.elf
	$(OBJDUMP) -d -S -g $(USER_DIR)/shell.elf > $(USER_DIR)/shell.asm

# Create binary image
$(USER_DIR)/shell.bin: $(USER_DIR)/shell.elf
	$(OBJCOPY) -O binary $(USER_DIR)/shell.elf $(USER_DIR)/shell.bin

else
# Non-logisim builds skip user
$(USER_DIR)/shell.bin:
	@echo "Skipping user build (only built for TARGET=logisim)"
endif

HOSTCC     := gcc
HOSTCFLAGS := -DHOST_BUILD -g -Wall -I$(KERNEL_DIR)


SETUP_SRC := kernel/setup.c

SETUP_EXE := kernel/setup.exe

$(SETUP_EXE): $(SETUP_SRC)
	$(HOSTCC) $(HOSTCFLAGS) -o $@ $(SETUP_SRC)

# Use setup to create hard drive image
hard_drive.bin: $(USER_DIR)/shell.bin $(SETUP_EXE)
	./$(SETUP_EXE) $(USER_DIR)/shell.bin $@





# -------- “logisim” convenience pipeline --------
logisim:
	$(MAKE) TARGET=logisim kernel.elf kernel.bin kernel.asm $(USER_DIR)/shell.elf $(USER_DIR)/shell.asm $(USER_DIR)/shell.bin hard_drive.bin

windows:
	$(MAKE) TARGET=windows

clean:
	rm -f $(KERNEL_OBJECTS) kernel.$(TARGET_EXT) kernel.bin kernel.asm kernel.map
	rm -f $(USER_SHELL_OBJS) $(USER_DIR)/shell.elf $(USER_DIR)/shell.bin
	rm -f $(SETUP)
	rm -f hard_drive.bin

.PHONY: all clean windows logisim
