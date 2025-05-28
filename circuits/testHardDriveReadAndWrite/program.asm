
program.elf:     file format elf32-littleriscv


Disassembly of section .init:

00000000 <_start>:

    .extern __stack_init      # address of the initial top of C call stack (calculated externally by linker)

	.globl _start
_start:                       # this is where CPU starts executing instructions after reset / power-on
	la sp,__stack_init        # initialise stack pointer (with the value that points to the last word of RAM)
   0:	00010117          	auipc	sp,0x10
   4:	7fc10113          	addi	sp,sp,2044 # 107fc <__stack_init>
	li a0,0                   # populate optional main() parameters with dummy values (just in case)
   8:	00000513          	li	a0,0
	li a1,0
   c:	00000593          	li	a1,0
	li a2,0
  10:	00000613          	li	a2,0
	jal main                  # call C main() function
  14:	040000ef          	jal	ra,54 <main>

00000018 <exit>:
exit:
	j exit                    # keep looping forever after main() returns
  18:	0000006f          	j	18 <exit>

Disassembly of section .text:

0000001c <printChar>:
#define CMD_WRITE_SECTOR 2

#define STATUS_BUSY  0x1
#define STATUS_ERROR 0x2

void printChar(char c) {
  1c:	fd010113          	addi	sp,sp,-48
  20:	02812623          	sw	s0,44(sp)
  24:	03010413          	addi	s0,sp,48
  28:	00050793          	mv	a5,a0
  2c:	fcf40fa3          	sb	a5,-33(s0)
    volatile char *p = (volatile char*)OUT_ADDR;
  30:	fff007b7          	lui	a5,0xfff00
  34:	fef42623          	sw	a5,-20(s0)
    *p = c;
  38:	fec42783          	lw	a5,-20(s0)
  3c:	fdf44703          	lbu	a4,-33(s0)
  40:	00e78023          	sb	a4,0(a5) # fff00000 <__stack_init+0xffeef804>
}
  44:	00000013          	nop
  48:	02c12403          	lw	s0,44(sp)
  4c:	03010113          	addi	sp,sp,48
  50:	00008067          	ret

00000054 <main>:
//volatile int* data;
int data[128];
int data2[128];
int canary;

int main(void) {
  54:	fe010113          	addi	sp,sp,-32
  58:	00112e23          	sw	ra,28(sp)
  5c:	00812c23          	sw	s0,24(sp)
  60:	02010413          	addi	s0,sp,32
    data[0] = 0xABCDABCD;
  64:	000107b7          	lui	a5,0x10
  68:	00078793          	mv	a5,a5
  6c:	abcdb737          	lui	a4,0xabcdb
  70:	bcd70713          	addi	a4,a4,-1075 # abcdabcd <__stack_init+0xabcca3d1>
  74:	00e7a023          	sw	a4,0(a5) # 10000 <data>

    for(int i = 0; i < 128; ++i){
  78:	fe042623          	sw	zero,-20(s0)
  7c:	02c0006f          	j	a8 <main+0x54>
        data[i] = i;
  80:	000107b7          	lui	a5,0x10
  84:	00078713          	mv	a4,a5
  88:	fec42783          	lw	a5,-20(s0)
  8c:	00279793          	slli	a5,a5,0x2
  90:	00f707b3          	add	a5,a4,a5
  94:	fec42703          	lw	a4,-20(s0)
  98:	00e7a023          	sw	a4,0(a5) # 10000 <data>
    for(int i = 0; i < 128; ++i){
  9c:	fec42783          	lw	a5,-20(s0)
  a0:	00178793          	addi	a5,a5,1
  a4:	fef42623          	sw	a5,-20(s0)
  a8:	fec42703          	lw	a4,-20(s0)
  ac:	07f00793          	li	a5,127
  b0:	fce7d8e3          	bge	a5,a4,80 <main+0x2c>
    }


    *hd_mem_addr = (int) data;
  b4:	1b802783          	lw	a5,440(zero) # 1b8 <hd_mem_addr>
  b8:	00010737          	lui	a4,0x10
  bc:	00070713          	mv	a4,a4
  c0:	00e7a023          	sw	a4,0(a5)
    *hd_sector = 0;
  c4:	1bc02783          	lw	a5,444(zero) # 1bc <hd_sector>
  c8:	0007a023          	sw	zero,0(a5)
    *hd_command = CMD_WRITE_SECTOR;
  cc:	1b402783          	lw	a5,436(zero) # 1b4 <hd_command>
  d0:	00200713          	li	a4,2
  d4:	00e7a023          	sw	a4,0(a5)

    while (*hd_status & STATUS_BUSY);
  d8:	00000013          	nop
  dc:	1c002783          	lw	a5,448(zero) # 1c0 <hd_status>
  e0:	0007a783          	lw	a5,0(a5)
  e4:	0017f793          	andi	a5,a5,1
  e8:	fe079ae3          	bnez	a5,dc <main+0x88>


    canary = 0xdeadbeef;
  ec:	000107b7          	lui	a5,0x10
  f0:	deadc737          	lui	a4,0xdeadc
  f4:	eef70713          	addi	a4,a4,-273 # deadbeef <__stack_init+0xdeacb6f3>
  f8:	40e7a023          	sw	a4,1024(a5) # 10400 <canary>
    *hd_mem_addr = (int) data2;
  fc:	1b802783          	lw	a5,440(zero) # 1b8 <hd_mem_addr>
 100:	00010737          	lui	a4,0x10
 104:	20070713          	addi	a4,a4,512 # 10200 <data2>
 108:	00e7a023          	sw	a4,0(a5)
    *hd_sector = 0;
 10c:	1bc02783          	lw	a5,444(zero) # 1bc <hd_sector>
 110:	0007a023          	sw	zero,0(a5)
    *hd_command = CMD_READ_SECTOR;
 114:	1b402783          	lw	a5,436(zero) # 1b4 <hd_command>
 118:	00100713          	li	a4,1
 11c:	00e7a023          	sw	a4,0(a5)

    while (*hd_status & STATUS_BUSY);
 120:	00000013          	nop
 124:	1c002783          	lw	a5,448(zero) # 1c0 <hd_status>
 128:	0007a783          	lw	a5,0(a5)
 12c:	0017f793          	andi	a5,a5,1
 130:	fe079ae3          	bnez	a5,124 <main+0xd0>

    int test = 1;
 134:	00100793          	li	a5,1
 138:	fef42423          	sw	a5,-24(s0)

    for(int i = 0; i < 128; ++i){
 13c:	fe042223          	sw	zero,-28(s0)
 140:	0340006f          	j	174 <main+0x120>
        if(data2[i] != i) test = 0;
 144:	000107b7          	lui	a5,0x10
 148:	20078713          	addi	a4,a5,512 # 10200 <data2>
 14c:	fe442783          	lw	a5,-28(s0)
 150:	00279793          	slli	a5,a5,0x2
 154:	00f707b3          	add	a5,a4,a5
 158:	0007a783          	lw	a5,0(a5)
 15c:	fe442703          	lw	a4,-28(s0)
 160:	00f70463          	beq	a4,a5,168 <main+0x114>
 164:	fe042423          	sw	zero,-24(s0)
    for(int i = 0; i < 128; ++i){
 168:	fe442783          	lw	a5,-28(s0)
 16c:	00178793          	addi	a5,a5,1
 170:	fef42223          	sw	a5,-28(s0)
 174:	fe442703          	lw	a4,-28(s0)
 178:	07f00793          	li	a5,127
 17c:	fce7d4e3          	bge	a5,a4,144 <main+0xf0>
    }


    if (test) {
 180:	fe842783          	lw	a5,-24(s0)
 184:	00078863          	beqz	a5,194 <main+0x140>
        printChar('+');
 188:	02b00513          	li	a0,43
 18c:	e91ff0ef          	jal	ra,1c <printChar>
 190:	00c0006f          	j	19c <main+0x148>
    } else {
        printChar('-');
 194:	02d00513          	li	a0,45
 198:	e85ff0ef          	jal	ra,1c <printChar>
    }

    return 0;
 19c:	00000793          	li	a5,0
 1a0:	00078513          	mv	a0,a5
 1a4:	01c12083          	lw	ra,28(sp)
 1a8:	01812403          	lw	s0,24(sp)
 1ac:	02010113          	addi	sp,sp,32
 1b0:	00008067          	ret
