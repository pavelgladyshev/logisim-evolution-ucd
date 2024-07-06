OUTPUT_FORMAT("elf32-littleriscv", "elf32-littleriscv", "elf32-littleriscv")
OUTPUT_ARCH(riscv)
ENTRY(_start)

MEMORY 
{
  rom (rxi)  : ORIGIN = 0x400000, LENGTH = 16384
  ram (w!i)   : ORIGIN = 0x80000000, LENGTH = 67108864 
}

SECTIONS
{
  .init : { *(.init) } > rom
  .text : { *(.text) } > rom
  .srodata : { *(.srodata) } > rom

  .bss : { *(.bss) *(COMMON) } > ram
  
  PROVIDE(__stack_init = ORIGIN(ram) + LENGTH(ram) - 4);
}

