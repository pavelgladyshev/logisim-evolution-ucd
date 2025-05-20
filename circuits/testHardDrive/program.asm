
program.elf:     file format elf32-littleriscv


Disassembly of section .init:

00000000 <_start>:
   0:	00010117          	auipc	sp,0x10
   4:	3fc10113          	addi	sp,sp,1020 # 103fc <__stack_init>
   8:	00000513          	li	a0,0
   c:	00000593          	li	a1,0
  10:	00000613          	li	a2,0
  14:	040000ef          	jal	ra,54 <main>

00000018 <exit>:
  18:	0000006f          	j	18 <exit>

Disassembly of section .text:

0000001c <printChar>:
  1c:	fd010113          	addi	sp,sp,-48
  20:	02812623          	sw	s0,44(sp)
  24:	03010413          	addi	s0,sp,48
  28:	00050793          	mv	a5,a0
  2c:	fcf40fa3          	sb	a5,-33(s0)
  30:	fff007b7          	lui	a5,0xfff00
  34:	fef42623          	sw	a5,-20(s0)
  38:	fec42783          	lw	a5,-20(s0)
  3c:	fdf44703          	lbu	a4,-33(s0)
  40:	00e78023          	sb	a4,0(a5) # fff00000 <__stack_init+0xffeefc04>
  44:	00000013          	nop
  48:	02c12403          	lw	s0,44(sp)
  4c:	03010113          	addi	sp,sp,48
  50:	00008067          	ret

00000054 <main>:
  54:	fe010113          	addi	sp,sp,-32
  58:	00112e23          	sw	ra,28(sp)
  5c:	00812c23          	sw	s0,24(sp)
  60:	02010413          	addi	s0,sp,32
  64:	000107b7          	lui	a5,0x10
  68:	fef42623          	sw	a5,-20(s0)
  6c:	fec42783          	lw	a5,-20(s0)
  70:	abcdb737          	lui	a4,0xabcdb
  74:	bcd70713          	addi	a4,a4,-1075 # abcdabcd <__stack_init+0xabcca7d1>
  78:	00e7a023          	sw	a4,0(a5) # 10000 <hd_status+0xfec4>
  7c:	13402783          	lw	a5,308(zero) # 134 <hd_mem_addr>
  80:	00010737          	lui	a4,0x10
  84:	00e7a023          	sw	a4,0(a5)
  88:	13802783          	lw	a5,312(zero) # 138 <hd_sector>
  8c:	0007a023          	sw	zero,0(a5)
  90:	13002783          	lw	a5,304(zero) # 130 <hd_command>
  94:	00200713          	li	a4,2
  98:	00e7a023          	sw	a4,0(a5)
  9c:	00000013          	nop
  a0:	13c02783          	lw	a5,316(zero) # 13c <hd_status>
  a4:	0007a783          	lw	a5,0(a5)
  a8:	0017f793          	andi	a5,a5,1
  ac:	fe079ae3          	bnez	a5,a0 <main+0x4c>
  b0:	fec42783          	lw	a5,-20(s0)
  b4:	0007a023          	sw	zero,0(a5)
  b8:	13402783          	lw	a5,308(zero) # 134 <hd_mem_addr>
  bc:	00010737          	lui	a4,0x10
  c0:	00e7a023          	sw	a4,0(a5)
  c4:	13802783          	lw	a5,312(zero) # 138 <hd_sector>
  c8:	00100713          	li	a4,1
  cc:	00e7a023          	sw	a4,0(a5)
  d0:	13002783          	lw	a5,304(zero) # 130 <hd_command>
  d4:	00100713          	li	a4,1
  d8:	00e7a023          	sw	a4,0(a5)
  dc:	00000013          	nop
  e0:	13c02783          	lw	a5,316(zero) # 13c <hd_status>
  e4:	0007a783          	lw	a5,0(a5)
  e8:	0017f793          	andi	a5,a5,1
  ec:	fe079ae3          	bnez	a5,e0 <main+0x8c>
  f0:	fec42783          	lw	a5,-20(s0)
  f4:	0007a703          	lw	a4,0(a5)
  f8:	abcdb7b7          	lui	a5,0xabcdb
  fc:	bcd78793          	addi	a5,a5,-1075 # abcdabcd <__stack_init+0xabcca7d1>
 100:	00f71863          	bne	a4,a5,110 <main+0xbc>
 104:	04b00513          	li	a0,75
 108:	f15ff0ef          	jal	ra,1c <printChar>
 10c:	00c0006f          	j	118 <main+0xc4>
 110:	04d00513          	li	a0,77
 114:	f09ff0ef          	jal	ra,1c <printChar>
 118:	00000793          	li	a5,0
 11c:	00078513          	mv	a0,a5
 120:	01c12083          	lw	ra,28(sp)
 124:	01812403          	lw	s0,24(sp)
 128:	02010113          	addi	sp,sp,32
 12c:	00008067          	ret
