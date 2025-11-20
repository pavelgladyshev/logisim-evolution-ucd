OUTPUT_FORMAT("elf32-littleriscv", "elf32-littleriscv", "elf32-littleriscv")
OUTPUT_ARCH(riscv)
ENTRY(_start)

MEMORY 
{
  rom (rxi)  : ORIGIN = 0x00000000, LENGTH = 65536
  ram (w!i)   : ORIGIN = 0x00010000, LENGTH = 0xB000  /* 45KB */
}

SECTIONS
{
  .init : { *(.init) } > rom
  .text : { *(.text) } > rom
  .srodata : { *(.srodata) } > rom
  .bss : { *(.bss) *(COMMON) } > ram
  
  PROVIDE(__stack_init = ORIGIN(ram) + LENGTH(ram) - 4);
}

