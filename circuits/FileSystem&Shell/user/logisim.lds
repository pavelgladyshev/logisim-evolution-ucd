OUTPUT_FORMAT("elf32-littleriscv", "elf32-littleriscv", "elf32-littleriscv")
OUTPUT_ARCH(riscv)
ENTRY(_start)

MEMORY 
{
  ram (w!i)   : ORIGIN = 0x0001B000, LENGTH = 0x5000  /* 20KB */
}

SECTIONS
{
  .init : { *(.init) } > ram
  .text : { *(.text) } > ram
  .srodata : { *(.srodata) } > ram
  .bss : { *(.bss) *(COMMON) } > ram
  
  PROVIDE(__stack_init = ORIGIN(ram) + LENGTH(ram) - 4);
}

