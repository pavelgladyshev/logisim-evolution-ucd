ENTRY(_start)
SECTIONS {
    . = 0x00020000;
    .init : {*.init}
    .text : {
        *(.text.entry)
        *(.text .text.*)
    }
    .rodata : { *(.rodata .rodata.*) }
    .data : { *(.data .data.*) }
    .bss : { *(.bss .bss.*) }
    /DISCARD/ : { *(.eh_frame) }
}