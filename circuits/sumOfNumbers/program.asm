
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
 164:	00200793          	li	a5,2
 168:	fef42623          	sw	a5,-20(s0)
 16c:	0280006f          	j	194 <main+0x40>
 170:	000107b7          	lui	a5,0x10
 174:	00078713          	mv	a4,a5
 178:	fec42783          	lw	a5,-20(s0)
 17c:	00f707b3          	add	a5,a4,a5
 180:	00100713          	li	a4,1
 184:	00e78023          	sb	a4,0(a5) # 10000 <is_prime>
 188:	fec42783          	lw	a5,-20(s0)
 18c:	00178793          	addi	a5,a5,1
 190:	fef42623          	sw	a5,-20(s0)
 194:	fec42703          	lw	a4,-20(s0)
 198:	06400793          	li	a5,100
 19c:	fce7dae3          	bge	a5,a4,170 <main+0x1c>
 1a0:	00200793          	li	a5,2
 1a4:	fef42423          	sw	a5,-24(s0)
 1a8:	0680006f          	j	210 <main+0xbc>
 1ac:	000107b7          	lui	a5,0x10
 1b0:	00078713          	mv	a4,a5
 1b4:	fe842783          	lw	a5,-24(s0)
 1b8:	00f707b3          	add	a5,a4,a5
 1bc:	0007c783          	lbu	a5,0(a5) # 10000 <is_prime>
 1c0:	04078263          	beqz	a5,204 <main+0xb0>
 1c4:	fe842783          	lw	a5,-24(s0)
 1c8:	02f787b3          	mul	a5,a5,a5
 1cc:	fef42223          	sw	a5,-28(s0)
 1d0:	0280006f          	j	1f8 <main+0xa4>
 1d4:	000107b7          	lui	a5,0x10
 1d8:	00078713          	mv	a4,a5
 1dc:	fe442783          	lw	a5,-28(s0)
 1e0:	00f707b3          	add	a5,a4,a5
 1e4:	00078023          	sb	zero,0(a5) # 10000 <is_prime>
 1e8:	fe442703          	lw	a4,-28(s0)
 1ec:	fe842783          	lw	a5,-24(s0)
 1f0:	00f707b3          	add	a5,a4,a5
 1f4:	fef42223          	sw	a5,-28(s0)
 1f8:	fe442703          	lw	a4,-28(s0)
 1fc:	06400793          	li	a5,100
 200:	fce7dae3          	bge	a5,a4,1d4 <main+0x80>
 204:	fe842783          	lw	a5,-24(s0)
 208:	00178793          	addi	a5,a5,1
 20c:	fef42423          	sw	a5,-24(s0)
 210:	fe842783          	lw	a5,-24(s0)
 214:	02f78733          	mul	a4,a5,a5
 218:	06400793          	li	a5,100
 21c:	f8e7d8e3          	bge	a5,a4,1ac <main+0x58>
 220:	2ac00513          	li	a0,684
 224:	df9ff0ef          	jal	ra,1c <printStr>
 228:	06400513          	li	a0,100
 22c:	e61ff0ef          	jal	ra,8c <printDigits>
 230:	2bc00513          	li	a0,700
 234:	de9ff0ef          	jal	ra,1c <printStr>
 238:	00200793          	li	a5,2
 23c:	fef42023          	sw	a5,-32(s0)
 240:	0380006f          	j	278 <main+0x124>
 244:	000107b7          	lui	a5,0x10
 248:	00078713          	mv	a4,a5
 24c:	fe042783          	lw	a5,-32(s0)
 250:	00f707b3          	add	a5,a4,a5
 254:	0007c783          	lbu	a5,0(a5) # 10000 <is_prime>
 258:	00078a63          	beqz	a5,26c <main+0x118>
 25c:	fe042503          	lw	a0,-32(s0)
 260:	e2dff0ef          	jal	ra,8c <printDigits>
 264:	2c000513          	li	a0,704
 268:	db5ff0ef          	jal	ra,1c <printStr>
 26c:	fe042783          	lw	a5,-32(s0)
 270:	00178793          	addi	a5,a5,1
 274:	fef42023          	sw	a5,-32(s0)
 278:	fe042703          	lw	a4,-32(s0)
 27c:	06400793          	li	a5,100
 280:	fce7d2e3          	bge	a5,a4,244 <main+0xf0>
 284:	fff007b7          	lui	a5,0xfff00
 288:	01078793          	addi	a5,a5,16 # fff00010 <__stack_init+0xffeefc14>
 28c:	00100713          	li	a4,1
 290:	00e7a023          	sw	a4,0(a5)
 294:	00000793          	li	a5,0
 298:	00078513          	mv	a0,a5
 29c:	01c12083          	lw	ra,28(sp)
 2a0:	01812403          	lw	s0,24(sp)
 2a4:	02010113          	addi	sp,sp,32
 2a8:	00008067          	ret
