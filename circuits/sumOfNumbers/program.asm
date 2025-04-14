
program.elf:     file format elf32-littleriscv


Disassembly of section .init:

00000000 <_start>:
   0:	00010117          	auipc	sp,0x10
   4:	3fc10113          	addi	sp,sp,1020 # 103fc <__stack_init>
   8:	00000513          	li	a0,0
   c:	00000593          	li	a1,0
  10:	00000613          	li	a2,0
  14:	140000ef          	jal	ra,154 <main>

00000018 <exit>:
  18:	0000006f          	j	18 <exit>

Disassembly of section .text:

0000001c <printStr>:
  1c:	fd010113          	addi	sp,sp,-48
  20:	02812623          	sw	s0,44(sp)
  24:	03010413          	addi	s0,sp,48
  28:	fca42e23          	sw	a0,-36(s0)
  2c:	fff007b7          	lui	a5,0xfff00
  30:	fef42423          	sw	a5,-24(s0)
  34:	fe042623          	sw	zero,-20(s0)
  38:	02c0006f          	j	64 <printStr+0x48>
  3c:	fec42783          	lw	a5,-20(s0)
  40:	fdc42703          	lw	a4,-36(s0)
  44:	00f707b3          	add	a5,a4,a5
  48:	0007c783          	lbu	a5,0(a5) # fff00000 <__stack_init+0xffeefc04>
  4c:	00078713          	mv	a4,a5
  50:	fe842783          	lw	a5,-24(s0)
  54:	00e7a023          	sw	a4,0(a5)
  58:	fec42783          	lw	a5,-20(s0)
  5c:	00178793          	addi	a5,a5,1
  60:	fef42623          	sw	a5,-20(s0)
  64:	fec42783          	lw	a5,-20(s0)
  68:	fdc42703          	lw	a4,-36(s0)
  6c:	00f707b3          	add	a5,a4,a5
  70:	0007c783          	lbu	a5,0(a5)
  74:	fc0794e3          	bnez	a5,3c <printStr+0x20>
  78:	00000013          	nop
  7c:	00000013          	nop
  80:	02c12403          	lw	s0,44(sp)
  84:	03010113          	addi	sp,sp,48
  88:	00008067          	ret

0000008c <printDigits>:
  8c:	fa010113          	addi	sp,sp,-96
  90:	04812e23          	sw	s0,92(sp)
  94:	06010413          	addi	s0,sp,96
  98:	faa42623          	sw	a0,-84(s0)
  9c:	fff007b7          	lui	a5,0xfff00
  a0:	fef42223          	sw	a5,-28(s0)
  a4:	fac42783          	lw	a5,-84(s0)
  a8:	00079a63          	bnez	a5,bc <printDigits+0x30>
  ac:	fe442783          	lw	a5,-28(s0)
  b0:	03000713          	li	a4,48
  b4:	00e7a023          	sw	a4,0(a5) # fff00000 <__stack_init+0xffeefc04>
  b8:	0900006f          	j	148 <printDigits+0xbc>
  bc:	fe042623          	sw	zero,-20(s0)
  c0:	03c0006f          	j	fc <printDigits+0x70>
  c4:	fec42783          	lw	a5,-20(s0)
  c8:	00178713          	addi	a4,a5,1
  cc:	fee42623          	sw	a4,-20(s0)
  d0:	fac42683          	lw	a3,-84(s0)
  d4:	00a00713          	li	a4,10
  d8:	02e6e733          	rem	a4,a3,a4
  dc:	00279793          	slli	a5,a5,0x2
  e0:	ff040693          	addi	a3,s0,-16
  e4:	00f687b3          	add	a5,a3,a5
  e8:	fce7a623          	sw	a4,-52(a5)
  ec:	fac42703          	lw	a4,-84(s0)
  f0:	00a00793          	li	a5,10
  f4:	02f747b3          	div	a5,a4,a5
  f8:	faf42623          	sw	a5,-84(s0)
  fc:	fac42783          	lw	a5,-84(s0)
 100:	fcf042e3          	bgtz	a5,c4 <printDigits+0x38>
 104:	fec42783          	lw	a5,-20(s0)
 108:	fff78793          	addi	a5,a5,-1
 10c:	fef42423          	sw	a5,-24(s0)
 110:	0300006f          	j	140 <printDigits+0xb4>
 114:	fe842783          	lw	a5,-24(s0)
 118:	00279793          	slli	a5,a5,0x2
 11c:	ff040713          	addi	a4,s0,-16
 120:	00f707b3          	add	a5,a4,a5
 124:	fcc7a783          	lw	a5,-52(a5)
 128:	03078713          	addi	a4,a5,48
 12c:	fe442783          	lw	a5,-28(s0)
 130:	00e7a023          	sw	a4,0(a5)
 134:	fe842783          	lw	a5,-24(s0)
 138:	fff78793          	addi	a5,a5,-1
 13c:	fef42423          	sw	a5,-24(s0)
 140:	fe842783          	lw	a5,-24(s0)
 144:	fc07d8e3          	bgez	a5,114 <printDigits+0x88>
 148:	05c12403          	lw	s0,92(sp)
 14c:	06010113          	addi	sp,sp,96
 150:	00008067          	ret

00000154 <main>:
 154:	fe010113          	addi	sp,sp,-32
 158:	00112e23          	sw	ra,28(sp)
 15c:	00812c23          	sw	s0,24(sp)
 160:	02010413          	addi	s0,sp,32
 164:	fe042623          	sw	zero,-20(s0)
 168:	0300006f          	j	198 <main+0x44>
 16c:	fec42783          	lw	a5,-20(s0)
 170:	00178713          	addi	a4,a5,1
 174:	000107b7          	lui	a5,0x10
 178:	00078693          	mv	a3,a5
 17c:	fec42783          	lw	a5,-20(s0)
 180:	00279793          	slli	a5,a5,0x2
 184:	00f687b3          	add	a5,a3,a5
 188:	00e7a023          	sw	a4,0(a5) # 10000 <array>
 18c:	fec42783          	lw	a5,-20(s0)
 190:	00178793          	addi	a5,a5,1
 194:	fef42623          	sw	a5,-20(s0)
 198:	fec42703          	lw	a4,-20(s0)
 19c:	00900793          	li	a5,9
 1a0:	fce7d6e3          	bge	a5,a4,16c <main+0x18>
 1a4:	fe042423          	sw	zero,-24(s0)
 1a8:	fe042223          	sw	zero,-28(s0)
 1ac:	0340006f          	j	1e0 <main+0x8c>
 1b0:	000107b7          	lui	a5,0x10
 1b4:	00078713          	mv	a4,a5
 1b8:	fe442783          	lw	a5,-28(s0)
 1bc:	00279793          	slli	a5,a5,0x2
 1c0:	00f707b3          	add	a5,a4,a5
 1c4:	0007a783          	lw	a5,0(a5) # 10000 <array>
 1c8:	fe842703          	lw	a4,-24(s0)
 1cc:	00f707b3          	add	a5,a4,a5
 1d0:	fef42423          	sw	a5,-24(s0)
 1d4:	fe442783          	lw	a5,-28(s0)
 1d8:	00178793          	addi	a5,a5,1
 1dc:	fef42223          	sw	a5,-28(s0)
 1e0:	fe442703          	lw	a4,-28(s0)
 1e4:	00900793          	li	a5,9
 1e8:	fce7d4e3          	bge	a5,a4,1b0 <main+0x5c>
 1ec:	22c00513          	li	a0,556
 1f0:	e2dff0ef          	jal	ra,1c <printStr>
 1f4:	00a00513          	li	a0,10
 1f8:	e95ff0ef          	jal	ra,8c <printDigits>
 1fc:	24800513          	li	a0,584
 200:	e1dff0ef          	jal	ra,1c <printStr>
 204:	fe842503          	lw	a0,-24(s0)
 208:	e85ff0ef          	jal	ra,8c <printDigits>
 20c:	25000513          	li	a0,592
 210:	e0dff0ef          	jal	ra,1c <printStr>
 214:	00000793          	li	a5,0
 218:	00078513          	mv	a0,a5
 21c:	01c12083          	lw	ra,28(sp)
 220:	01812403          	lw	s0,24(sp)
 224:	02010113          	addi	sp,sp,32
 228:	00008067          	ret
