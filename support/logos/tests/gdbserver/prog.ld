/* The ROM at 0 (64 KiB) and the RAM at 0x00100000 (64 KiB used), as in the supplement's simple-c circuit */
ENTRY(_start)
MEMORY {
    rom (rx) : ORIGIN = 0x00000000, LENGTH = 64K
    ram (rw) : ORIGIN = 0x00100000, LENGTH = 64K
}
SECTIONS {
    .text : { *(.text.start) *(.text*) *(.rodata*) *(.srodata*) } > rom
    .bss : { *(.sbss*) *(.bss*) *(COMMON) } > ram
}
