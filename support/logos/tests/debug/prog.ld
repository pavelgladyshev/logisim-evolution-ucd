ENTRY(_start)
SECTIONS {
    . = 0;
    .text : { *(.text.start) *(.text*) }
    .data : { *(.rodata*) *(.data*) *(.sdata*) }
    .bss : { *(.sbss*) *(.bss*) *(COMMON) }
}
