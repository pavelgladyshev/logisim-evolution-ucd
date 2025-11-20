# =========================
# Build targets & toolchain
# =========================
TARGET ?= windows

KERNEL_DIR := kernel
USER_DIR   := user

# ================
# macOS RISC-V
# ================
ifeq ($(TARGET), mac)
    CC       := riscv64-elf-gcc
    OBJCOPY  := riscv64-elf-objcopy
    OBJDUMP  := riscv64-elf-objdump

    # Correct ISA for your trap handlers
    CFLAGS := -march=rv32im_zicsr -mabi=ilp32 -ffreestanding -g -DMACOS \
              -I. -I$(KERNEL_DIR) -I$(USER_DIR)

    USER_CFLAGS := $(CFLAGS) -D__USER_SPACE__

    LDFLAGS := -Wl,-T$(KERNEL_DIR)/logisim.lds,-T$(KERNEL_DIR)/kernel.ld \
               -Wl,-Map=kernel.map -nostartfiles -nostdlib

    TARGET_EXT := elf
    PLATFORM_SOURCE := $(KERNEL_DIR)/platform_logisim.c

# ================
# Windows RISC-V (original Logisim build)
# ================
else ifeq ($(TARGET), logisim)
    CC       := riscv-none-embed-gcc
    OBJCOPY  := riscv-none-embed-objcopy
    OBJDUMP  := riscv-none-embed-objdump

    CFLAGS := -march=rv32im -mstrict-align -nostdlib -g -DLOGISIM \
              -I. -I$(KERNEL_DIR) -I$(USER_DIR)

    USER_CFLAGS := $(CFLAGS) -D__USER_SPACE__

    LDFLAGS := -Wl,-T$(KERNEL_DIR)/logisim.lds,-T$(KERNEL_DIR)/kernel.ld \
               -Wl,-Map=kernel.map -nostartfiles

    TARGET_EXT := elf
    PLATFORM_SOURCE := $(KERNEL_DIR)/platform_logisim.c

# ================
# Windows native app
# ================
else
    CC       := x86_64-w64-mingw32-gcc
    CFLAGS   := -g -DWINDOWS -I. -I$(KERNEL_DIR) -I$(USER_DIR)
    USER_CFLAGS := $(CFLAGS)
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

KERNEL_ASM_SOURCES := \
    $(KERNEL_DIR)/crt0.S \
    $(KERNEL_DIR)/trap.S

KERNEL_C_OBJS   := $(KERNEL_C_SOURCES:.c=.o)
KERNEL_ASM_OBJS := $(KERNEL_DIR)/crt0.o $(KERNEL_DIR)/trap_asm.o
KERNEL_OBJECTS  := $(KERNEL_C_OBJS) $(KERNEL_ASM_OBJS)

# =========================
# User (shell) sources
# =========================
USER_CRT0     := $(USER_DIR)/crt0.S
USER_CRT0_OBJ := $(USER_DIR)/crt0.o

USER_SHELL_SRCS := \
    $(USER_DIR)/main.c \
    $(USER_DIR)/shell.c \
    $(USER_DIR)/syscalls.c \
    $(KERNEL_DIR)/utils.c

USER_SHELL_OBJS := $(USER_SHELL_SRCS:.c=.o) $(USER_CRT0_OBJ)


# =========================
# Setup tool (host utility)
# =========================
HOSTCC     := gcc
HOSTCFLAGS := -DHOST_BUILD -g -Wall -I$(KERNEL_DIR)
SETUP_EXE  := kernel/setup.exe
SETUP_SRC  := kernel/setup.c


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


$(KERNEL_DIR)/crt0.o: $(KERNEL_DIR)/crt0.S
	$(CC) $(CFLAGS) -c -o $@ $<

$(KERNEL_DIR)/trap_asm.o: $(KERNEL_DIR)/trap.S
	$(CC) $(CFLAGS) -c -o $@ $<

%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<


# -------- User (logisim/mac) --------
ifeq ($(TARGET), logisim)
    BUILD_USER := yes
else ifeq ($(TARGET), mac)
    BUILD_USER := yes
else
    BUILD_USER := no
endif


ifeq ($(BUILD_USER), yes)

$(USER_CRT0_OBJ): $(USER_CRT0)
	$(CC) $(USER_CFLAGS) -c -o $@ $<

$(USER_DIR)/%.o: $(USER_DIR)/%.c
	$(CC) $(USER_CFLAGS) -c -o $@ $<

$(KERNEL_DIR)/utils.o: $(KERNEL_DIR)/utils.c
	$(CC) $(USER_CFLAGS) -c -o $@ $<

$(USER_DIR)/shell.elf: $(USER_SHELL_OBJS) $(USER_DIR)/logisim.lds
	$(CC) $(USER_CFLAGS) -nostdlib -Wl,-T$(USER_DIR)/logisim.lds -o $@ $(USER_SHELL_OBJS)

$(USER_DIR)/shell.bin: $(USER_DIR)/shell.elf
	$(OBJCOPY) -O binary $< $@

$(USER_DIR)/shell.asm: $(USER_DIR)/shell.elf
	$(OBJDUMP) -d -S -g $< > $@

else
$(USER_DIR)/shell.bin:
	@echo "Skipping user build (TARGET = windows)"
endif


# -------- Setup Tool --------
$(SETUP_EXE): $(SETUP_SRC)
	$(HOSTCC) $(HOSTCFLAGS) -o $@ $(SETUP_SRC)

hard_drive.bin: $(USER_DIR)/shell.bin $(SETUP_EXE)
	./$(SETUP_EXE) $(USER_DIR)/shell.bin $@


# -------- Convenience --------
logisim:
	$(MAKE) TARGET=logisim kernel.elf kernel.bin kernel.asm \
	    $(USER_DIR)/shell.elf $(USER_DIR)/shell.asm $(USER_DIR)/shell.bin hard_drive.bin

mac:
	$(MAKE) TARGET=mac kernel.elf kernel.bin kernel.asm \
	    $(USER_DIR)/shell.elf $(USER_DIR)/shell.asm $(USER_DIR)/shell.bin hard_drive.bin

clean:
	rm -f $(KERNEL_OBJECTS) kernel.$(TARGET_EXT) kernel.bin kernel.asm kernel.map
	rm -f $(USER_SHELL_OBJS) $(USER_DIR)/shell.elf $(USER_DIR)/shell.bin $(USER_DIR)/shell.asm
	rm -f $(SETUP_EXE)
	rm -f hard_drive.bin

.PHONY: all clean logisim mac
