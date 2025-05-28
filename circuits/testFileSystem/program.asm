
program.elf:     file format elf32-littleriscv


Disassembly of section .init:

00000000 <_start>:

    .extern __stack_init      # address of the initial top of C call stack (calculated externally by linker)

	.globl _start
_start:                       # this is where CPU starts executing instructions after reset / power-on
	la sp,__stack_init        # initialise stack pointer (with the value that points to the last word of RAM)
   0:	00020117          	auipc	sp,0x20
   4:	ffc10113          	addi	sp,sp,-4 # 1fffc <__stack_init>
	li a0,0                   # populate optional main() parameters with dummy values (just in case)
   8:	00000513          	li	a0,0
	li a1,0
   c:	00000593          	li	a1,0
	li a2,0
  10:	00000613          	li	a2,0
	jal main                  # call C main() function
  14:	038000ef          	jal	ra,4c <main>

00000018 <exit>:
exit:
	j exit                    # keep looping forever after main() returns
  18:	0000006f          	j	18 <exit>

Disassembly of section .text:

0000001c <print>:
#include "fc.h"
#include <string.h>

#define OUT_ADDR 0xFFF00000

void print(char c) {
      1c:	fe010113          	addi	sp,sp,-32
      20:	00812e23          	sw	s0,28(sp)
      24:	02010413          	addi	s0,sp,32
      28:	00050793          	mv	a5,a0
      2c:	fef407a3          	sb	a5,-17(s0)
    *(volatile char*)OUT_ADDR = c;
      30:	fff007b7          	lui	a5,0xfff00
      34:	fef44703          	lbu	a4,-17(s0)
      38:	00e78023          	sb	a4,0(a5) # fff00000 <__stack_init+0xffee0004>
}
      3c:	00000013          	nop
      40:	01c12403          	lw	s0,28(sp)
      44:	02010113          	addi	sp,sp,32
      48:	00008067          	ret

0000004c <main>:

int main() {
      4c:	f5010113          	addi	sp,sp,-176
      50:	0a112623          	sw	ra,172(sp)
      54:	0a812423          	sw	s0,168(sp)
      58:	0b010413          	addi	s0,sp,176
       print('+');
      5c:	02b00513          	li	a0,43
      60:	fbdff0ef          	jal	ra,1c <print>

      if (fs_format() < 0) {
      64:	53c000ef          	jal	ra,5a0 <fs_format>
      68:	00050793          	mv	a5,a0
      6c:	0007da63          	bgez	a5,80 <main+0x34>
         print('F');
      70:	04600513          	li	a0,70
      74:	fa9ff0ef          	jal	ra,1c <print>
         return -1;
      78:	fff00793          	li	a5,-1
      7c:	1900006f          	j	20c <main+0x1c0>
     }



     if (fs_init() < 0) {
      80:	464000ef          	jal	ra,4e4 <fs_init>
      84:	00050793          	mv	a5,a0
      88:	0007da63          	bgez	a5,9c <main+0x50>
         print('I');
      8c:	04900513          	li	a0,73
      90:	f8dff0ef          	jal	ra,1c <print>
         return -1;
      94:	fff00793          	li	a5,-1
      98:	1740006f          	j	20c <main+0x1c0>
     }


     if (iopen(1) < 0) {
      9c:	00100513          	li	a0,1
      a0:	6d0000ef          	jal	ra,770 <iopen>
      a4:	00050793          	mv	a5,a0
      a8:	0007da63          	bgez	a5,bc <main+0x70>
         print('O');
      ac:	04f00513          	li	a0,79
      b0:	f6dff0ef          	jal	ra,1c <print>
         return -1;
      b4:	fff00793          	li	a5,-1
      b8:	1540006f          	j	20c <main+0x1c0>
     }

     int fd = fs_open_inode(1);
      bc:	00100513          	li	a0,1
      c0:	2d8000ef          	jal	ra,398 <fs_open_inode>
      c4:	fea42423          	sw	a0,-24(s0)
     if (fd < 0) {
      c8:	fe842783          	lw	a5,-24(s0)
      cc:	0007da63          	bgez	a5,e0 <main+0x94>
         print('E');
      d0:	04500513          	li	a0,69
      d4:	f49ff0ef          	jal	ra,1c <print>
         return -1;
      d8:	fff00793          	li	a5,-1
      dc:	1300006f          	j	20c <main+0x1c0>
     }

     fileTable[fd].flags = O_RDWR;
      e0:	000107b7          	lui	a5,0x10
      e4:	00078693          	mv	a3,a5
      e8:	fe842703          	lw	a4,-24(s0)
      ec:	00070793          	mv	a5,a4
      f0:	00179793          	slli	a5,a5,0x1
      f4:	00e787b3          	add	a5,a5,a4
      f8:	00279793          	slli	a5,a5,0x2
      fc:	00f687b3          	add	a5,a3,a5
     100:	00300713          	li	a4,3
     104:	00e7a423          	sw	a4,8(a5) # 10008 <fileTable+0x8>


     const char *msg = "Hello";
     108:	000017b7          	lui	a5,0x1
     10c:	26c78793          	addi	a5,a5,620 # 126c <strncpy+0xac>
     110:	fef42223          	sw	a5,-28(s0)
     int w = write(fd, msg, strlen(msg));
     114:	fe442503          	lw	a0,-28(s0)
     118:	715000ef          	jal	ra,102c <strlen>
     11c:	00050793          	mv	a5,a0
     120:	00078613          	mv	a2,a5
     124:	fe442583          	lw	a1,-28(s0)
     128:	fe842503          	lw	a0,-24(s0)
     12c:	281000ef          	jal	ra,bac <write>
     130:	fea42023          	sw	a0,-32(s0)
     if (w != strlen(msg)) {
     134:	fe442503          	lw	a0,-28(s0)
     138:	6f5000ef          	jal	ra,102c <strlen>
     13c:	00050713          	mv	a4,a0
     140:	fe042783          	lw	a5,-32(s0)
     144:	00f70a63          	beq	a4,a5,158 <main+0x10c>
         print('W');
     148:	05700513          	li	a0,87
     14c:	ed1ff0ef          	jal	ra,1c <print>
         return -1;
     150:	fff00793          	li	a5,-1
     154:	0b80006f          	j	20c <main+0x1c0>
     } else {
     print('-');
     158:	02d00513          	li	a0,45
     15c:	ec1ff0ef          	jal	ra,1c <print>
     }


     fs_seek(fd, 0);
     160:	00000593          	li	a1,0
     164:	fe842503          	lw	a0,-24(s0)
     168:	314000ef          	jal	ra,47c <fs_seek>


     char buf[128] = {0};
     16c:	f4042e23          	sw	zero,-164(s0)
     170:	f6040793          	addi	a5,s0,-160
     174:	07c00713          	li	a4,124
     178:	00070613          	mv	a2,a4
     17c:	00000593          	li	a1,0
     180:	00078513          	mv	a0,a5
     184:	76d000ef          	jal	ra,10f0 <memset>
     int n = read(fd, buf, sizeof(buf));
     188:	f5c40793          	addi	a5,s0,-164
     18c:	08000613          	li	a2,128
     190:	00078593          	mv	a1,a5
     194:	fe842503          	lw	a0,-24(s0)
     198:	6b0000ef          	jal	ra,848 <read>
     19c:	fca42e23          	sw	a0,-36(s0)
     if (n < 0) {
     1a0:	fdc42783          	lw	a5,-36(s0)
     1a4:	0007da63          	bgez	a5,1b8 <main+0x16c>
         print('R');
     1a8:	05200513          	li	a0,82
     1ac:	e71ff0ef          	jal	ra,1c <print>
         return -1;
     1b0:	fff00793          	li	a5,-1
     1b4:	0580006f          	j	20c <main+0x1c0>
     } else if (n == 0) {
     1b8:	fdc42783          	lw	a5,-36(s0)
     1bc:	00079a63          	bnez	a5,1d0 <main+0x184>
         print('Z');
     1c0:	05a00513          	li	a0,90
     1c4:	e59ff0ef          	jal	ra,1c <print>
         return 0;
     1c8:	00000793          	li	a5,0
     1cc:	0400006f          	j	20c <main+0x1c0>
     }


     for (int i = 0; i < n; i++) {
     1d0:	fe042623          	sw	zero,-20(s0)
     1d4:	0280006f          	j	1fc <main+0x1b0>
         print(buf[i]);
     1d8:	fec42783          	lw	a5,-20(s0)
     1dc:	ff040713          	addi	a4,s0,-16
     1e0:	00f707b3          	add	a5,a4,a5
     1e4:	f6c7c783          	lbu	a5,-148(a5)
     1e8:	00078513          	mv	a0,a5
     1ec:	e31ff0ef          	jal	ra,1c <print>
     for (int i = 0; i < n; i++) {
     1f0:	fec42783          	lw	a5,-20(s0)
     1f4:	00178793          	addi	a5,a5,1
     1f8:	fef42623          	sw	a5,-20(s0)
     1fc:	fec42703          	lw	a4,-20(s0)
     200:	fdc42783          	lw	a5,-36(s0)
     204:	fcf74ae3          	blt	a4,a5,1d8 <main+0x18c>
     }

     return 0;
     208:	00000793          	li	a5,0
}
     20c:	00078513          	mv	a0,a5
     210:	0ac12083          	lw	ra,172(sp)
     214:	0a812403          	lw	s0,168(sp)
     218:	0b010113          	addi	sp,sp,176
     21c:	00008067          	ret

00000220 <hd_wait>:
static unsigned char bitmap[BLOCK_SIZE];
static struct inode inodes[MAXFILES];
static int fdCounter = 0;
file_entry_t fileTable[MAXFILES];

inline static void hd_wait() {
     220:	ff010113          	addi	sp,sp,-16
     224:	00812623          	sw	s0,12(sp)
     228:	01010413          	addi	s0,sp,16
    while (*hd_status & 0x1);
     22c:	00000013          	nop
     230:	000017b7          	lui	a5,0x1
     234:	2807a783          	lw	a5,640(a5) # 1280 <hd_status>
     238:	0007a783          	lw	a5,0(a5)
     23c:	0017f793          	andi	a5,a5,1
     240:	fe0798e3          	bnez	a5,230 <hd_wait+0x10>
}
     244:	00000013          	nop
     248:	00000013          	nop
     24c:	00c12403          	lw	s0,12(sp)
     250:	01010113          	addi	sp,sp,16
     254:	00008067          	ret

00000258 <read_block>:


static int read_block(int blockNumber, void *buf) {
     258:	fe010113          	addi	sp,sp,-32
     25c:	00112e23          	sw	ra,28(sp)
     260:	00812c23          	sw	s0,24(sp)
     264:	02010413          	addi	s0,sp,32
     268:	fea42623          	sw	a0,-20(s0)
     26c:	feb42423          	sw	a1,-24(s0)
    if(blockNumber < 0 || blockNumber >= sb.blocksNumber) return -1;
     270:	fec42783          	lw	a5,-20(s0)
     274:	0007cc63          	bltz	a5,28c <read_block+0x34>
     278:	000107b7          	lui	a5,0x10
     27c:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     280:	0047a783          	lw	a5,4(a5)
     284:	fec42703          	lw	a4,-20(s0)
     288:	00f74663          	blt	a4,a5,294 <read_block+0x3c>
     28c:	fff00793          	li	a5,-1
     290:	0580006f          	j	2e8 <read_block+0x90>
    *hd_mem_addr = (int)buf;
     294:	000017b7          	lui	a5,0x1
     298:	2787a783          	lw	a5,632(a5) # 1278 <hd_mem_addr>
     29c:	fe842703          	lw	a4,-24(s0)
     2a0:	00e7a023          	sw	a4,0(a5)
    *hd_sector = blockNumber;
     2a4:	000017b7          	lui	a5,0x1
     2a8:	27c7a783          	lw	a5,636(a5) # 127c <hd_sector>
     2ac:	fec42703          	lw	a4,-20(s0)
     2b0:	00e7a023          	sw	a4,0(a5)
    *hd_command = 1;
     2b4:	000017b7          	lui	a5,0x1
     2b8:	2747a783          	lw	a5,628(a5) # 1274 <hd_command>
     2bc:	00100713          	li	a4,1
     2c0:	00e7a023          	sw	a4,0(a5)
    hd_wait();
     2c4:	f5dff0ef          	jal	ra,220 <hd_wait>
    return (*hd_status & 0x2) ? -1 : 0;
     2c8:	000017b7          	lui	a5,0x1
     2cc:	2807a783          	lw	a5,640(a5) # 1280 <hd_status>
     2d0:	0007a783          	lw	a5,0(a5)
     2d4:	0027f793          	andi	a5,a5,2
     2d8:	00078663          	beqz	a5,2e4 <read_block+0x8c>
     2dc:	fff00793          	li	a5,-1
     2e0:	0080006f          	j	2e8 <read_block+0x90>
     2e4:	00000793          	li	a5,0
}
     2e8:	00078513          	mv	a0,a5
     2ec:	01c12083          	lw	ra,28(sp)
     2f0:	01812403          	lw	s0,24(sp)
     2f4:	02010113          	addi	sp,sp,32
     2f8:	00008067          	ret

000002fc <write_block>:

static int write_block(int blockNumber, const void *buf) {
     2fc:	fe010113          	addi	sp,sp,-32
     300:	00112e23          	sw	ra,28(sp)
     304:	00812c23          	sw	s0,24(sp)
     308:	02010413          	addi	s0,sp,32
     30c:	fea42623          	sw	a0,-20(s0)
     310:	feb42423          	sw	a1,-24(s0)
    if(blockNumber >= sb.blocksNumber) return -1;
     314:	000107b7          	lui	a5,0x10
     318:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     31c:	0047a783          	lw	a5,4(a5)
     320:	fec42703          	lw	a4,-20(s0)
     324:	00f74663          	blt	a4,a5,330 <write_block+0x34>
     328:	fff00793          	li	a5,-1
     32c:	0580006f          	j	384 <write_block+0x88>
    *hd_mem_addr = (int)buf;
     330:	000017b7          	lui	a5,0x1
     334:	2787a783          	lw	a5,632(a5) # 1278 <hd_mem_addr>
     338:	fe842703          	lw	a4,-24(s0)
     33c:	00e7a023          	sw	a4,0(a5)
    *hd_sector = blockNumber;
     340:	000017b7          	lui	a5,0x1
     344:	27c7a783          	lw	a5,636(a5) # 127c <hd_sector>
     348:	fec42703          	lw	a4,-20(s0)
     34c:	00e7a023          	sw	a4,0(a5)
    *hd_command = 2;
     350:	000017b7          	lui	a5,0x1
     354:	2747a783          	lw	a5,628(a5) # 1274 <hd_command>
     358:	00200713          	li	a4,2
     35c:	00e7a023          	sw	a4,0(a5)
    hd_wait();
     360:	ec1ff0ef          	jal	ra,220 <hd_wait>
    return (*hd_status & 0x2) ? -1 : 0;
     364:	000017b7          	lui	a5,0x1
     368:	2807a783          	lw	a5,640(a5) # 1280 <hd_status>
     36c:	0007a783          	lw	a5,0(a5)
     370:	0027f793          	andi	a5,a5,2
     374:	00078663          	beqz	a5,380 <write_block+0x84>
     378:	fff00793          	li	a5,-1
     37c:	0080006f          	j	384 <write_block+0x88>
     380:	00000793          	li	a5,0
}
     384:	00078513          	mv	a0,a5
     388:	01c12083          	lw	ra,28(sp)
     38c:	01812403          	lw	s0,24(sp)
     390:	02010113          	addi	sp,sp,32
     394:	00008067          	ret

00000398 <fs_open_inode>:

int fs_open_inode(int inum) {
     398:	fd010113          	addi	sp,sp,-48
     39c:	02112623          	sw	ra,44(sp)
     3a0:	02812423          	sw	s0,40(sp)
     3a4:	03010413          	addi	s0,sp,48
     3a8:	fca42e23          	sw	a0,-36(s0)
    if (iopen(inum) < 0) return -1;
     3ac:	fdc42503          	lw	a0,-36(s0)
     3b0:	3c0000ef          	jal	ra,770 <iopen>
     3b4:	00050793          	mv	a5,a0
     3b8:	0007d663          	bgez	a5,3c4 <fs_open_inode+0x2c>
     3bc:	fff00793          	li	a5,-1
     3c0:	0a80006f          	j	468 <fs_open_inode+0xd0>

    int fd = fdCounter++;
     3c4:	000107b7          	lui	a5,0x10
     3c8:	6d47a783          	lw	a5,1748(a5) # 106d4 <fdCounter>
     3cc:	00178693          	addi	a3,a5,1
     3d0:	00010737          	lui	a4,0x10
     3d4:	6cd72a23          	sw	a3,1748(a4) # 106d4 <fdCounter>
     3d8:	fef42623          	sw	a5,-20(s0)
    if (fd >= MAXFILES) return -1;
     3dc:	fec42703          	lw	a4,-20(s0)
     3e0:	00f00793          	li	a5,15
     3e4:	00e7d663          	bge	a5,a4,3f0 <fs_open_inode+0x58>
     3e8:	fff00793          	li	a5,-1
     3ec:	07c0006f          	j	468 <fs_open_inode+0xd0>


    fileTable[fd].inum  = inum;
     3f0:	000107b7          	lui	a5,0x10
     3f4:	00078693          	mv	a3,a5
     3f8:	fec42703          	lw	a4,-20(s0)
     3fc:	00070793          	mv	a5,a4
     400:	00179793          	slli	a5,a5,0x1
     404:	00e787b3          	add	a5,a5,a4
     408:	00279793          	slli	a5,a5,0x2
     40c:	00f687b3          	add	a5,a3,a5
     410:	fdc42703          	lw	a4,-36(s0)
     414:	00e7a223          	sw	a4,4(a5) # 10004 <fileTable+0x4>
    fileTable[fd].flags = O_RDONLY;
     418:	000107b7          	lui	a5,0x10
     41c:	00078693          	mv	a3,a5
     420:	fec42703          	lw	a4,-20(s0)
     424:	00070793          	mv	a5,a4
     428:	00179793          	slli	a5,a5,0x1
     42c:	00e787b3          	add	a5,a5,a4
     430:	00279793          	slli	a5,a5,0x2
     434:	00f687b3          	add	a5,a3,a5
     438:	00100713          	li	a4,1
     43c:	00e7a423          	sw	a4,8(a5) # 10008 <fileTable+0x8>
    fileTable[fd].pos   = 0;
     440:	000107b7          	lui	a5,0x10
     444:	00078693          	mv	a3,a5
     448:	fec42703          	lw	a4,-20(s0)
     44c:	00070793          	mv	a5,a4
     450:	00179793          	slli	a5,a5,0x1
     454:	00e787b3          	add	a5,a5,a4
     458:	00279793          	slli	a5,a5,0x2
     45c:	00f687b3          	add	a5,a3,a5
     460:	0007a023          	sw	zero,0(a5) # 10000 <fileTable>
    return fd;
     464:	fec42783          	lw	a5,-20(s0)
}
     468:	00078513          	mv	a0,a5
     46c:	02c12083          	lw	ra,44(sp)
     470:	02812403          	lw	s0,40(sp)
     474:	03010113          	addi	sp,sp,48
     478:	00008067          	ret

0000047c <fs_seek>:

void fs_seek(int fd, int pos) {
     47c:	fe010113          	addi	sp,sp,-32
     480:	00812e23          	sw	s0,28(sp)
     484:	02010413          	addi	s0,sp,32
     488:	fea42623          	sw	a0,-20(s0)
     48c:	feb42423          	sw	a1,-24(s0)
    if (fd >= 0 && fd < MAXFILES && pos >= 0)
     490:	fec42783          	lw	a5,-20(s0)
     494:	0407c063          	bltz	a5,4d4 <fs_seek+0x58>
     498:	fec42703          	lw	a4,-20(s0)
     49c:	00f00793          	li	a5,15
     4a0:	02e7ca63          	blt	a5,a4,4d4 <fs_seek+0x58>
     4a4:	fe842783          	lw	a5,-24(s0)
     4a8:	0207c663          	bltz	a5,4d4 <fs_seek+0x58>
        fileTable[fd].pos = pos;
     4ac:	000107b7          	lui	a5,0x10
     4b0:	00078693          	mv	a3,a5
     4b4:	fec42703          	lw	a4,-20(s0)
     4b8:	00070793          	mv	a5,a4
     4bc:	00179793          	slli	a5,a5,0x1
     4c0:	00e787b3          	add	a5,a5,a4
     4c4:	00279793          	slli	a5,a5,0x2
     4c8:	00f687b3          	add	a5,a3,a5
     4cc:	fe842703          	lw	a4,-24(s0)
     4d0:	00e7a023          	sw	a4,0(a5) # 10000 <fileTable>
}
     4d4:	00000013          	nop
     4d8:	01c12403          	lw	s0,28(sp)
     4dc:	02010113          	addi	sp,sp,32
     4e0:	00008067          	ret

000004e4 <fs_init>:

int fs_init(void) {
     4e4:	ff010113          	addi	sp,sp,-16
     4e8:	00112623          	sw	ra,12(sp)
     4ec:	00812423          	sw	s0,8(sp)
     4f0:	01010413          	addi	s0,sp,16
    memset(&sb, 0, sizeof sb);
     4f4:	01400613          	li	a2,20
     4f8:	00000593          	li	a1,0
     4fc:	000107b7          	lui	a5,0x10
     500:	0c078513          	addi	a0,a5,192 # 100c0 <sb>
     504:	3ed000ef          	jal	ra,10f0 <memset>
    memset(fileTable, 0, sizeof fileTable);
     508:	0c000613          	li	a2,192
     50c:	00000593          	li	a1,0
     510:	000107b7          	lui	a5,0x10
     514:	00078513          	mv	a0,a5
     518:	3d9000ef          	jal	ra,10f0 <memset>
    fdCounter = 0;
     51c:	000107b7          	lui	a5,0x10
     520:	6c07aa23          	sw	zero,1748(a5) # 106d4 <fdCounter>

    if (read_block(1, &sb) < 0) return -1;
     524:	000107b7          	lui	a5,0x10
     528:	0c078593          	addi	a1,a5,192 # 100c0 <sb>
     52c:	00100513          	li	a0,1
     530:	d29ff0ef          	jal	ra,258 <read_block>
     534:	00050793          	mv	a5,a0
     538:	0007d663          	bgez	a5,544 <fs_init+0x60>
     53c:	fff00793          	li	a5,-1
     540:	04c0006f          	j	58c <fs_init+0xa8>

    if (sb.inodesNumber == 0) return -1;
     544:	000107b7          	lui	a5,0x10
     548:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     54c:	0007a783          	lw	a5,0(a5)
     550:	00079663          	bnez	a5,55c <fs_init+0x78>
     554:	fff00793          	li	a5,-1
     558:	0340006f          	j	58c <fs_init+0xa8>

    if (read_block(sb.bitmapStart, bitmap) < 0) return -1;
     55c:	000107b7          	lui	a5,0x10
     560:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     564:	0087a703          	lw	a4,8(a5)
     568:	000107b7          	lui	a5,0x10
     56c:	0d478593          	addi	a1,a5,212 # 100d4 <bitmap>
     570:	00070513          	mv	a0,a4
     574:	ce5ff0ef          	jal	ra,258 <read_block>
     578:	00050793          	mv	a5,a0
     57c:	0007d663          	bgez	a5,588 <fs_init+0xa4>
     580:	fff00793          	li	a5,-1
     584:	0080006f          	j	58c <fs_init+0xa8>

    return 0;
     588:	00000793          	li	a5,0
}
     58c:	00078513          	mv	a0,a5
     590:	00c12083          	lw	ra,12(sp)
     594:	00812403          	lw	s0,8(sp)
     598:	01010113          	addi	sp,sp,16
     59c:	00008067          	ret

000005a0 <fs_format>:

int fs_format(void) {
     5a0:	de010113          	addi	sp,sp,-544
     5a4:	20112e23          	sw	ra,540(sp)
     5a8:	20812c23          	sw	s0,536(sp)
     5ac:	22010413          	addi	s0,sp,544
    //memset(&sb, 0, sizeof sb);
    //memset(bitmap, 0, sizeof bitmap);
    //memset(inodes, 0, sizeof inodes);
    //memset(fileTable, 0, sizeof fileTable);
    fdCounter = 0;
     5b0:	000107b7          	lui	a5,0x10
     5b4:	6c07aa23          	sw	zero,1748(a5) # 106d4 <fdCounter>

    sb.inodesNumber = MAXFILES;
     5b8:	000107b7          	lui	a5,0x10
     5bc:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     5c0:	01000713          	li	a4,16
     5c4:	00e7a023          	sw	a4,0(a5)
    sb.blocksNumber = 1024;
     5c8:	000107b7          	lui	a5,0x10
     5cc:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     5d0:	40000713          	li	a4,1024
     5d4:	00e7a223          	sw	a4,4(a5)
    sb.bitmapStart  = 2;
     5d8:	000107b7          	lui	a5,0x10
     5dc:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     5e0:	00200713          	li	a4,2
     5e4:	00e7a423          	sw	a4,8(a5)
    sb.inodeStart   = 3;
     5e8:	000107b7          	lui	a5,0x10
     5ec:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     5f0:	00300713          	li	a4,3
     5f4:	00e7a623          	sw	a4,12(a5)
    sb.dataStart    = 5;
     5f8:	000107b7          	lui	a5,0x10
     5fc:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     600:	00500713          	li	a4,5
     604:	00e7a823          	sw	a4,16(a5)
    if (write_block(1, &sb) < 0) return -1;
     608:	000107b7          	lui	a5,0x10
     60c:	0c078593          	addi	a1,a5,192 # 100c0 <sb>
     610:	00100513          	li	a0,1
     614:	ce9ff0ef          	jal	ra,2fc <write_block>
     618:	00050793          	mv	a5,a0
     61c:	0007d663          	bgez	a5,628 <fs_format+0x88>
     620:	fff00793          	li	a5,-1
     624:	1380006f          	j	75c <fs_format+0x1bc>


    for (int i = 0; i < sb.dataStart; i++)
     628:	fe042623          	sw	zero,-20(s0)
     62c:	08c0006f          	j	6b8 <fs_format+0x118>
        bitmap[i / 8] |= (1 << (i % 8));
     630:	fec42783          	lw	a5,-20(s0)
     634:	41f7d713          	srai	a4,a5,0x1f
     638:	00777713          	andi	a4,a4,7
     63c:	00f707b3          	add	a5,a4,a5
     640:	4037d793          	srai	a5,a5,0x3
     644:	00078693          	mv	a3,a5
     648:	000107b7          	lui	a5,0x10
     64c:	0d478793          	addi	a5,a5,212 # 100d4 <bitmap>
     650:	00d787b3          	add	a5,a5,a3
     654:	0007c783          	lbu	a5,0(a5)
     658:	01879613          	slli	a2,a5,0x18
     65c:	41865613          	srai	a2,a2,0x18
     660:	fec42703          	lw	a4,-20(s0)
     664:	41f75793          	srai	a5,a4,0x1f
     668:	01d7d793          	srli	a5,a5,0x1d
     66c:	00f70733          	add	a4,a4,a5
     670:	00777713          	andi	a4,a4,7
     674:	40f707b3          	sub	a5,a4,a5
     678:	00078713          	mv	a4,a5
     67c:	00100793          	li	a5,1
     680:	00e797b3          	sll	a5,a5,a4
     684:	01879793          	slli	a5,a5,0x18
     688:	4187d793          	srai	a5,a5,0x18
     68c:	00f667b3          	or	a5,a2,a5
     690:	01879793          	slli	a5,a5,0x18
     694:	4187d793          	srai	a5,a5,0x18
     698:	0ff7f713          	zext.b	a4,a5
     69c:	000107b7          	lui	a5,0x10
     6a0:	0d478793          	addi	a5,a5,212 # 100d4 <bitmap>
     6a4:	00d787b3          	add	a5,a5,a3
     6a8:	00e78023          	sb	a4,0(a5)
    for (int i = 0; i < sb.dataStart; i++)
     6ac:	fec42783          	lw	a5,-20(s0)
     6b0:	00178793          	addi	a5,a5,1
     6b4:	fef42623          	sw	a5,-20(s0)
     6b8:	000107b7          	lui	a5,0x10
     6bc:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     6c0:	0107a783          	lw	a5,16(a5)
     6c4:	fec42703          	lw	a4,-20(s0)
     6c8:	f6f744e3          	blt	a4,a5,630 <fs_format+0x90>

    if (write_block(sb.bitmapStart, bitmap) < 0) return -1;
     6cc:	000107b7          	lui	a5,0x10
     6d0:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     6d4:	0087a703          	lw	a4,8(a5)
     6d8:	000107b7          	lui	a5,0x10
     6dc:	0d478593          	addi	a1,a5,212 # 100d4 <bitmap>
     6e0:	00070513          	mv	a0,a4
     6e4:	c19ff0ef          	jal	ra,2fc <write_block>
     6e8:	00050793          	mv	a5,a0
     6ec:	0007d663          	bgez	a5,6f8 <fs_format+0x158>
     6f0:	fff00793          	li	a5,-1
     6f4:	0680006f          	j	75c <fs_format+0x1bc>

    unsigned char zeroBlock[BLOCK_SIZE] = {0};
     6f8:	de042423          	sw	zero,-536(s0)
     6fc:	dec40793          	addi	a5,s0,-532
     700:	1fc00713          	li	a4,508
     704:	00070613          	mv	a2,a4
     708:	00000593          	li	a1,0
     70c:	00078513          	mv	a0,a5
     710:	1e1000ef          	jal	ra,10f0 <memset>
    for (int i = sb.inodeStart; i < sb.dataStart; i++)
     714:	000107b7          	lui	a5,0x10
     718:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     71c:	00c7a783          	lw	a5,12(a5)
     720:	fef42423          	sw	a5,-24(s0)
     724:	0200006f          	j	744 <fs_format+0x1a4>
        write_block(i, zeroBlock);
     728:	de840793          	addi	a5,s0,-536
     72c:	00078593          	mv	a1,a5
     730:	fe842503          	lw	a0,-24(s0)
     734:	bc9ff0ef          	jal	ra,2fc <write_block>
    for (int i = sb.inodeStart; i < sb.dataStart; i++)
     738:	fe842783          	lw	a5,-24(s0)
     73c:	00178793          	addi	a5,a5,1
     740:	fef42423          	sw	a5,-24(s0)
     744:	000107b7          	lui	a5,0x10
     748:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     74c:	0107a783          	lw	a5,16(a5)
     750:	fe842703          	lw	a4,-24(s0)
     754:	fcf74ae3          	blt	a4,a5,728 <fs_format+0x188>

    return 0;
     758:	00000793          	li	a5,0
}
     75c:	00078513          	mv	a0,a5
     760:	21c12083          	lw	ra,540(sp)
     764:	21812403          	lw	s0,536(sp)
     768:	22010113          	addi	sp,sp,544
     76c:	00008067          	ret

00000770 <iopen>:



int iopen(int inum) {
     770:	dd010113          	addi	sp,sp,-560
     774:	22112623          	sw	ra,556(sp)
     778:	22812423          	sw	s0,552(sp)
     77c:	23010413          	addi	s0,sp,560
     780:	dca42e23          	sw	a0,-548(s0)
    if(inum < 0 || inum >= sb.inodesNumber) return -1;
     784:	ddc42783          	lw	a5,-548(s0)
     788:	0007cc63          	bltz	a5,7a0 <iopen+0x30>
     78c:	000107b7          	lui	a5,0x10
     790:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     794:	0007a783          	lw	a5,0(a5)
     798:	ddc42703          	lw	a4,-548(s0)
     79c:	00f74663          	blt	a4,a5,7a8 <iopen+0x38>
     7a0:	fff00793          	li	a5,-1
     7a4:	0900006f          	j	834 <iopen+0xc4>

    int blockOffset = sb.inodeStart + (inum * INODE_SIZE) / BLOCK_SIZE;
     7a8:	000107b7          	lui	a5,0x10
     7ac:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     7b0:	00c7a703          	lw	a4,12(a5)
     7b4:	ddc42783          	lw	a5,-548(s0)
     7b8:	41f7d693          	srai	a3,a5,0x1f
     7bc:	0076f693          	andi	a3,a3,7
     7c0:	00f687b3          	add	a5,a3,a5
     7c4:	4037d793          	srai	a5,a5,0x3
     7c8:	00f707b3          	add	a5,a4,a5
     7cc:	fef42623          	sw	a5,-20(s0)
    int byteOffset = (inum * INODE_SIZE) % BLOCK_SIZE;
     7d0:	ddc42783          	lw	a5,-548(s0)
     7d4:	00679713          	slli	a4,a5,0x6
     7d8:	41f75793          	srai	a5,a4,0x1f
     7dc:	0177d793          	srli	a5,a5,0x17
     7e0:	00f70733          	add	a4,a4,a5
     7e4:	1ff77713          	andi	a4,a4,511
     7e8:	40f707b3          	sub	a5,a4,a5
     7ec:	fef42423          	sw	a5,-24(s0)

    unsigned char block[BLOCK_SIZE];
    read_block(blockOffset, block);
     7f0:	de840793          	addi	a5,s0,-536
     7f4:	00078593          	mv	a1,a5
     7f8:	fec42503          	lw	a0,-20(s0)
     7fc:	a5dff0ef          	jal	ra,258 <read_block>
    memcpy(&inodes[inum], block + byteOffset, sizeof(struct inode));
     800:	ddc42783          	lw	a5,-548(s0)
     804:	00679713          	slli	a4,a5,0x6
     808:	000107b7          	lui	a5,0x10
     80c:	2d478793          	addi	a5,a5,724 # 102d4 <inodes>
     810:	00f706b3          	add	a3,a4,a5
     814:	fe842783          	lw	a5,-24(s0)
     818:	de840713          	addi	a4,s0,-536
     81c:	00f707b3          	add	a5,a4,a5
     820:	04000613          	li	a2,64
     824:	00078593          	mv	a1,a5
     828:	00068513          	mv	a0,a3
     82c:	125000ef          	jal	ra,1150 <memcpy>

    return inum;
     830:	ddc42783          	lw	a5,-548(s0)
}
     834:	00078513          	mv	a0,a5
     838:	22c12083          	lw	ra,556(sp)
     83c:	22812403          	lw	s0,552(sp)
     840:	23010113          	addi	sp,sp,560
     844:	00008067          	ret

00000848 <read>:


int read(int fd, void *buf, int n) {
     848:	dc010113          	addi	sp,sp,-576
     84c:	22112e23          	sw	ra,572(sp)
     850:	22812c23          	sw	s0,568(sp)
     854:	24010413          	addi	s0,sp,576
     858:	dca42623          	sw	a0,-564(s0)
     85c:	dcb42423          	sw	a1,-568(s0)
     860:	dcc42223          	sw	a2,-572(s0)
    if (fd < 0 || fd >= fdCounter || !(fileTable[fd].flags & (O_RDONLY|O_RDWR)))
     864:	dcc42783          	lw	a5,-564(s0)
     868:	0407c063          	bltz	a5,8a8 <read+0x60>
     86c:	000107b7          	lui	a5,0x10
     870:	6d47a783          	lw	a5,1748(a5) # 106d4 <fdCounter>
     874:	dcc42703          	lw	a4,-564(s0)
     878:	02f75863          	bge	a4,a5,8a8 <read+0x60>
     87c:	000107b7          	lui	a5,0x10
     880:	00078693          	mv	a3,a5
     884:	dcc42703          	lw	a4,-564(s0)
     888:	00070793          	mv	a5,a4
     88c:	00179793          	slli	a5,a5,0x1
     890:	00e787b3          	add	a5,a5,a4
     894:	00279793          	slli	a5,a5,0x2
     898:	00f687b3          	add	a5,a3,a5
     89c:	0087a783          	lw	a5,8(a5) # 10008 <fileTable+0x8>
     8a0:	0037f793          	andi	a5,a5,3
     8a4:	00079663          	bnez	a5,8b0 <read+0x68>
        return -1;
     8a8:	fff00793          	li	a5,-1
     8ac:	2180006f          	j	ac4 <read+0x27c>
    if (n <= 0) return 0;
     8b0:	dc442783          	lw	a5,-572(s0)
     8b4:	00f04663          	bgtz	a5,8c0 <read+0x78>
     8b8:	00000793          	li	a5,0
     8bc:	2080006f          	j	ac4 <read+0x27c>

    struct inode *ip = &inodes[fileTable[fd].inum]; //pointer to inode copy in ram
     8c0:	000107b7          	lui	a5,0x10
     8c4:	00078693          	mv	a3,a5
     8c8:	dcc42703          	lw	a4,-564(s0)
     8cc:	00070793          	mv	a5,a4
     8d0:	00179793          	slli	a5,a5,0x1
     8d4:	00e787b3          	add	a5,a5,a4
     8d8:	00279793          	slli	a5,a5,0x2
     8dc:	00f687b3          	add	a5,a3,a5
     8e0:	0047a783          	lw	a5,4(a5) # 10004 <fileTable+0x4>
     8e4:	00679713          	slli	a4,a5,0x6
     8e8:	000107b7          	lui	a5,0x10
     8ec:	2d478793          	addi	a5,a5,724 # 102d4 <inodes>
     8f0:	00f707b3          	add	a5,a4,a5
     8f4:	fef42023          	sw	a5,-32(s0)
    int done = 0, left = n;
     8f8:	fe042623          	sw	zero,-20(s0)
     8fc:	dc442783          	lw	a5,-572(s0)
     900:	fef42423          	sw	a5,-24(s0)
    unsigned char block[BLOCK_SIZE];

    while (left > 0 && fileTable[fd].pos < ip->size) {
     904:	1740006f          	j	a78 <read+0x230>
        int blockIndex = fileTable[fd].pos / BLOCK_SIZE;
     908:	000107b7          	lui	a5,0x10
     90c:	00078693          	mv	a3,a5
     910:	dcc42703          	lw	a4,-564(s0)
     914:	00070793          	mv	a5,a4
     918:	00179793          	slli	a5,a5,0x1
     91c:	00e787b3          	add	a5,a5,a4
     920:	00279793          	slli	a5,a5,0x2
     924:	00f687b3          	add	a5,a3,a5
     928:	0007a783          	lw	a5,0(a5) # 10000 <fileTable>
     92c:	41f7d713          	srai	a4,a5,0x1f
     930:	1ff77713          	andi	a4,a4,511
     934:	00f707b3          	add	a5,a4,a5
     938:	4097d793          	srai	a5,a5,0x9
     93c:	fcf42e23          	sw	a5,-36(s0)
        int offset = fileTable[fd].pos % BLOCK_SIZE;
     940:	000107b7          	lui	a5,0x10
     944:	00078693          	mv	a3,a5
     948:	dcc42703          	lw	a4,-564(s0)
     94c:	00070793          	mv	a5,a4
     950:	00179793          	slli	a5,a5,0x1
     954:	00e787b3          	add	a5,a5,a4
     958:	00279793          	slli	a5,a5,0x2
     95c:	00f687b3          	add	a5,a3,a5
     960:	0007a703          	lw	a4,0(a5) # 10000 <fileTable>
     964:	41f75793          	srai	a5,a4,0x1f
     968:	0177d793          	srli	a5,a5,0x17
     96c:	00f70733          	add	a4,a4,a5
     970:	1ff77713          	andi	a4,a4,511
     974:	40f707b3          	sub	a5,a4,a5
     978:	fcf42c23          	sw	a5,-40(s0)
        int chunk = BLOCK_SIZE - offset;
     97c:	20000713          	li	a4,512
     980:	fd842783          	lw	a5,-40(s0)
     984:	40f707b3          	sub	a5,a4,a5
     988:	fef42223          	sw	a5,-28(s0)

        if (chunk > left) chunk = left;
     98c:	fe442703          	lw	a4,-28(s0)
     990:	fe842783          	lw	a5,-24(s0)
     994:	00e7d663          	bge	a5,a4,9a0 <read+0x158>
     998:	fe842783          	lw	a5,-24(s0)
     99c:	fef42223          	sw	a5,-28(s0)
        if (blockIndex >= MAX_NUMBER_OF_BLOCKS_IN_FILE) break;
     9a0:	fdc42703          	lw	a4,-36(s0)
     9a4:	00c00793          	li	a5,12
     9a8:	10e7c663          	blt	a5,a4,ab4 <read+0x26c>
        if (read_block(ip->direct[blockIndex], block) < 0) break;
     9ac:	fe042703          	lw	a4,-32(s0)
     9b0:	fdc42783          	lw	a5,-36(s0)
     9b4:	00279793          	slli	a5,a5,0x2
     9b8:	00f707b3          	add	a5,a4,a5
     9bc:	0047a783          	lw	a5,4(a5)
     9c0:	dd840713          	addi	a4,s0,-552
     9c4:	00070593          	mv	a1,a4
     9c8:	00078513          	mv	a0,a5
     9cc:	88dff0ef          	jal	ra,258 <read_block>
     9d0:	00050793          	mv	a5,a0
     9d4:	0e07c463          	bltz	a5,abc <read+0x274>

        memcpy(buf, block + offset, chunk);
     9d8:	fd842783          	lw	a5,-40(s0)
     9dc:	dd840713          	addi	a4,s0,-552
     9e0:	00f707b3          	add	a5,a4,a5
     9e4:	fe442703          	lw	a4,-28(s0)
     9e8:	00070613          	mv	a2,a4
     9ec:	00078593          	mv	a1,a5
     9f0:	dc842503          	lw	a0,-568(s0)
     9f4:	75c000ef          	jal	ra,1150 <memcpy>
        buf = (char*)buf + chunk;
     9f8:	fe442783          	lw	a5,-28(s0)
     9fc:	dc842703          	lw	a4,-568(s0)
     a00:	00f707b3          	add	a5,a4,a5
     a04:	dcf42423          	sw	a5,-568(s0)
        done += chunk;
     a08:	fec42703          	lw	a4,-20(s0)
     a0c:	fe442783          	lw	a5,-28(s0)
     a10:	00f707b3          	add	a5,a4,a5
     a14:	fef42623          	sw	a5,-20(s0)
        left -= chunk;
     a18:	fe842703          	lw	a4,-24(s0)
     a1c:	fe442783          	lw	a5,-28(s0)
     a20:	40f707b3          	sub	a5,a4,a5
     a24:	fef42423          	sw	a5,-24(s0)
        fileTable[fd].pos += chunk;
     a28:	000107b7          	lui	a5,0x10
     a2c:	00078693          	mv	a3,a5
     a30:	dcc42703          	lw	a4,-564(s0)
     a34:	00070793          	mv	a5,a4
     a38:	00179793          	slli	a5,a5,0x1
     a3c:	00e787b3          	add	a5,a5,a4
     a40:	00279793          	slli	a5,a5,0x2
     a44:	00f687b3          	add	a5,a3,a5
     a48:	0007a703          	lw	a4,0(a5) # 10000 <fileTable>
     a4c:	fe442783          	lw	a5,-28(s0)
     a50:	00f706b3          	add	a3,a4,a5
     a54:	000107b7          	lui	a5,0x10
     a58:	00078613          	mv	a2,a5
     a5c:	dcc42703          	lw	a4,-564(s0)
     a60:	00070793          	mv	a5,a4
     a64:	00179793          	slli	a5,a5,0x1
     a68:	00e787b3          	add	a5,a5,a4
     a6c:	00279793          	slli	a5,a5,0x2
     a70:	00f607b3          	add	a5,a2,a5
     a74:	00d7a023          	sw	a3,0(a5) # 10000 <fileTable>
    while (left > 0 && fileTable[fd].pos < ip->size) {
     a78:	fe842783          	lw	a5,-24(s0)
     a7c:	04f05263          	blez	a5,ac0 <read+0x278>
     a80:	000107b7          	lui	a5,0x10
     a84:	00078693          	mv	a3,a5
     a88:	dcc42703          	lw	a4,-564(s0)
     a8c:	00070793          	mv	a5,a4
     a90:	00179793          	slli	a5,a5,0x1
     a94:	00e787b3          	add	a5,a5,a4
     a98:	00279793          	slli	a5,a5,0x2
     a9c:	00f687b3          	add	a5,a3,a5
     aa0:	0007a703          	lw	a4,0(a5) # 10000 <fileTable>
     aa4:	fe042783          	lw	a5,-32(s0)
     aa8:	0007a783          	lw	a5,0(a5)
     aac:	e4f74ee3          	blt	a4,a5,908 <read+0xc0>
     ab0:	0100006f          	j	ac0 <read+0x278>
        if (blockIndex >= MAX_NUMBER_OF_BLOCKS_IN_FILE) break;
     ab4:	00000013          	nop
     ab8:	0080006f          	j	ac0 <read+0x278>
        if (read_block(ip->direct[blockIndex], block) < 0) break;
     abc:	00000013          	nop
    }
    return done;
     ac0:	fec42783          	lw	a5,-20(s0)
}
     ac4:	00078513          	mv	a0,a5
     ac8:	23c12083          	lw	ra,572(sp)
     acc:	23812403          	lw	s0,568(sp)
     ad0:	24010113          	addi	sp,sp,576
     ad4:	00008067          	ret

00000ad8 <flush_inode>:

static int flush_inode(int inum) {
     ad8:	dd010113          	addi	sp,sp,-560
     adc:	22112623          	sw	ra,556(sp)
     ae0:	22812423          	sw	s0,552(sp)
     ae4:	23010413          	addi	s0,sp,560
     ae8:	dca42e23          	sw	a0,-548(s0)
    int offsetBlock = sb.inodeStart + (inum * INODE_SIZE) / BLOCK_SIZE;
     aec:	000107b7          	lui	a5,0x10
     af0:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     af4:	00c7a703          	lw	a4,12(a5)
     af8:	ddc42783          	lw	a5,-548(s0)
     afc:	41f7d693          	srai	a3,a5,0x1f
     b00:	0076f693          	andi	a3,a3,7
     b04:	00f687b3          	add	a5,a3,a5
     b08:	4037d793          	srai	a5,a5,0x3
     b0c:	00f707b3          	add	a5,a4,a5
     b10:	fef42623          	sw	a5,-20(s0)
    int offsetByte  = (inum * INODE_SIZE) % BLOCK_SIZE;
     b14:	ddc42783          	lw	a5,-548(s0)
     b18:	00679713          	slli	a4,a5,0x6
     b1c:	41f75793          	srai	a5,a4,0x1f
     b20:	0177d793          	srli	a5,a5,0x17
     b24:	00f70733          	add	a4,a4,a5
     b28:	1ff77713          	andi	a4,a4,511
     b2c:	40f707b3          	sub	a5,a4,a5
     b30:	fef42423          	sw	a5,-24(s0)
    unsigned char block[BLOCK_SIZE];
    if (read_block(offsetBlock, block) < 0) return -1;
     b34:	de840793          	addi	a5,s0,-536
     b38:	00078593          	mv	a1,a5
     b3c:	fec42503          	lw	a0,-20(s0)
     b40:	f18ff0ef          	jal	ra,258 <read_block>
     b44:	00050793          	mv	a5,a0
     b48:	0007d663          	bgez	a5,b54 <flush_inode+0x7c>
     b4c:	fff00793          	li	a5,-1
     b50:	0480006f          	j	b98 <flush_inode+0xc0>
    memcpy(block + offsetByte, &inodes[inum], sizeof(struct inode));
     b54:	fe842783          	lw	a5,-24(s0)
     b58:	de840713          	addi	a4,s0,-536
     b5c:	00f706b3          	add	a3,a4,a5
     b60:	ddc42783          	lw	a5,-548(s0)
     b64:	00679713          	slli	a4,a5,0x6
     b68:	000107b7          	lui	a5,0x10
     b6c:	2d478793          	addi	a5,a5,724 # 102d4 <inodes>
     b70:	00f707b3          	add	a5,a4,a5
     b74:	04000613          	li	a2,64
     b78:	00078593          	mv	a1,a5
     b7c:	00068513          	mv	a0,a3
     b80:	5d0000ef          	jal	ra,1150 <memcpy>
    return write_block(offsetBlock, block);
     b84:	de840793          	addi	a5,s0,-536
     b88:	00078593          	mv	a1,a5
     b8c:	fec42503          	lw	a0,-20(s0)
     b90:	f6cff0ef          	jal	ra,2fc <write_block>
     b94:	00050793          	mv	a5,a0
}
     b98:	00078513          	mv	a0,a5
     b9c:	22c12083          	lw	ra,556(sp)
     ba0:	22812403          	lw	s0,552(sp)
     ba4:	23010113          	addi	sp,sp,560
     ba8:	00008067          	ret

00000bac <write>:


int write(int fd, const void *buf, int n) {
     bac:	dc010113          	addi	sp,sp,-576
     bb0:	22112e23          	sw	ra,572(sp)
     bb4:	22812c23          	sw	s0,568(sp)
     bb8:	24010413          	addi	s0,sp,576
     bbc:	dca42623          	sw	a0,-564(s0)
     bc0:	dcb42423          	sw	a1,-568(s0)
     bc4:	dcc42223          	sw	a2,-572(s0)
    if (fd < 0 || fd >= fdCounter || !(fileTable[fd].flags & (O_WRONLY|O_RDWR)))
     bc8:	dcc42783          	lw	a5,-564(s0)
     bcc:	0407c063          	bltz	a5,c0c <write+0x60>
     bd0:	000107b7          	lui	a5,0x10
     bd4:	6d47a783          	lw	a5,1748(a5) # 106d4 <fdCounter>
     bd8:	dcc42703          	lw	a4,-564(s0)
     bdc:	02f75863          	bge	a4,a5,c0c <write+0x60>
     be0:	000107b7          	lui	a5,0x10
     be4:	00078693          	mv	a3,a5
     be8:	dcc42703          	lw	a4,-564(s0)
     bec:	00070793          	mv	a5,a4
     bf0:	00179793          	slli	a5,a5,0x1
     bf4:	00e787b3          	add	a5,a5,a4
     bf8:	00279793          	slli	a5,a5,0x2
     bfc:	00f687b3          	add	a5,a3,a5
     c00:	0087a783          	lw	a5,8(a5) # 10008 <fileTable+0x8>
     c04:	0037f793          	andi	a5,a5,3
     c08:	00079663          	bnez	a5,c14 <write+0x68>
        return -1;
     c0c:	fff00793          	li	a5,-1
     c10:	4080006f          	j	1018 <write+0x46c>

    if (n <= 0) return 0;
     c14:	dc442783          	lw	a5,-572(s0)
     c18:	00f04663          	bgtz	a5,c24 <write+0x78>
     c1c:	00000793          	li	a5,0
     c20:	3f80006f          	j	1018 <write+0x46c>

    struct inode *ip = &inodes[fileTable[fd].inum];
     c24:	000107b7          	lui	a5,0x10
     c28:	00078693          	mv	a3,a5
     c2c:	dcc42703          	lw	a4,-564(s0)
     c30:	00070793          	mv	a5,a4
     c34:	00179793          	slli	a5,a5,0x1
     c38:	00e787b3          	add	a5,a5,a4
     c3c:	00279793          	slli	a5,a5,0x2
     c40:	00f687b3          	add	a5,a3,a5
     c44:	0047a783          	lw	a5,4(a5) # 10004 <fileTable+0x4>
     c48:	00679713          	slli	a4,a5,0x6
     c4c:	000107b7          	lui	a5,0x10
     c50:	2d478793          	addi	a5,a5,724 # 102d4 <inodes>
     c54:	00f707b3          	add	a5,a4,a5
     c58:	fcf42e23          	sw	a5,-36(s0)
    int done = 0, left = n;
     c5c:	fe042623          	sw	zero,-20(s0)
     c60:	dc442783          	lw	a5,-572(s0)
     c64:	fef42423          	sw	a5,-24(s0)
    unsigned char block[BLOCK_SIZE];

    while (left > 0) {
     c68:	30c0006f          	j	f74 <write+0x3c8>
        int blockIndex = fileTable[fd].pos / BLOCK_SIZE;
     c6c:	000107b7          	lui	a5,0x10
     c70:	00078693          	mv	a3,a5
     c74:	dcc42703          	lw	a4,-564(s0)
     c78:	00070793          	mv	a5,a4
     c7c:	00179793          	slli	a5,a5,0x1
     c80:	00e787b3          	add	a5,a5,a4
     c84:	00279793          	slli	a5,a5,0x2
     c88:	00f687b3          	add	a5,a3,a5
     c8c:	0007a783          	lw	a5,0(a5) # 10000 <fileTable>
     c90:	41f7d713          	srai	a4,a5,0x1f
     c94:	1ff77713          	andi	a4,a4,511
     c98:	00f707b3          	add	a5,a4,a5
     c9c:	4097d793          	srai	a5,a5,0x9
     ca0:	fcf42c23          	sw	a5,-40(s0)
        int offset = fileTable[fd].pos % BLOCK_SIZE;
     ca4:	000107b7          	lui	a5,0x10
     ca8:	00078693          	mv	a3,a5
     cac:	dcc42703          	lw	a4,-564(s0)
     cb0:	00070793          	mv	a5,a4
     cb4:	00179793          	slli	a5,a5,0x1
     cb8:	00e787b3          	add	a5,a5,a4
     cbc:	00279793          	slli	a5,a5,0x2
     cc0:	00f687b3          	add	a5,a3,a5
     cc4:	0007a703          	lw	a4,0(a5) # 10000 <fileTable>
     cc8:	41f75793          	srai	a5,a4,0x1f
     ccc:	0177d793          	srli	a5,a5,0x17
     cd0:	00f70733          	add	a4,a4,a5
     cd4:	1ff77713          	andi	a4,a4,511
     cd8:	40f707b3          	sub	a5,a4,a5
     cdc:	fcf42a23          	sw	a5,-44(s0)
        int chunk = BLOCK_SIZE - offset;
     ce0:	20000713          	li	a4,512
     ce4:	fd442783          	lw	a5,-44(s0)
     ce8:	40f707b3          	sub	a5,a4,a5
     cec:	fef42223          	sw	a5,-28(s0)
        if (chunk > left) chunk = left;
     cf0:	fe442703          	lw	a4,-28(s0)
     cf4:	fe842783          	lw	a5,-24(s0)
     cf8:	00e7d663          	bge	a5,a4,d04 <write+0x158>
     cfc:	fe842783          	lw	a5,-24(s0)
     d00:	fef42223          	sw	a5,-28(s0)
        if (blockIndex >= MAX_NUMBER_OF_BLOCKS_IN_FILE) break;
     d04:	fd842703          	lw	a4,-40(s0)
     d08:	00c00793          	li	a5,12
     d0c:	26e7ca63          	blt	a5,a4,f80 <write+0x3d4>

        if (ip->direct[blockIndex] == 0) {
     d10:	fdc42703          	lw	a4,-36(s0)
     d14:	fd842783          	lw	a5,-40(s0)
     d18:	00279793          	slli	a5,a5,0x2
     d1c:	00f707b3          	add	a5,a4,a5
     d20:	0047a783          	lw	a5,4(a5)
     d24:	16079463          	bnez	a5,e8c <write+0x2e0>
            for (int i = 0; i < sb.blocksNumber - sb.dataStart; i++) {
     d28:	fe042023          	sw	zero,-32(s0)
     d2c:	1240006f          	j	e50 <write+0x2a4>
                if (!(bitmap[i / 8] & (1 << (i % 8)))) {
     d30:	fe042783          	lw	a5,-32(s0)
     d34:	41f7d713          	srai	a4,a5,0x1f
     d38:	00777713          	andi	a4,a4,7
     d3c:	00f707b3          	add	a5,a4,a5
     d40:	4037d793          	srai	a5,a5,0x3
     d44:	00078713          	mv	a4,a5
     d48:	000107b7          	lui	a5,0x10
     d4c:	0d478793          	addi	a5,a5,212 # 100d4 <bitmap>
     d50:	00e787b3          	add	a5,a5,a4
     d54:	0007c783          	lbu	a5,0(a5)
     d58:	00078693          	mv	a3,a5
     d5c:	fe042703          	lw	a4,-32(s0)
     d60:	41f75793          	srai	a5,a4,0x1f
     d64:	01d7d793          	srli	a5,a5,0x1d
     d68:	00f70733          	add	a4,a4,a5
     d6c:	00777713          	andi	a4,a4,7
     d70:	40f707b3          	sub	a5,a4,a5
     d74:	40f6d7b3          	sra	a5,a3,a5
     d78:	0017f793          	andi	a5,a5,1
     d7c:	0c079463          	bnez	a5,e44 <write+0x298>
                    bitmap[i / 8] |= (1 << (i % 8));
     d80:	fe042783          	lw	a5,-32(s0)
     d84:	41f7d713          	srai	a4,a5,0x1f
     d88:	00777713          	andi	a4,a4,7
     d8c:	00f707b3          	add	a5,a4,a5
     d90:	4037d793          	srai	a5,a5,0x3
     d94:	00078693          	mv	a3,a5
     d98:	000107b7          	lui	a5,0x10
     d9c:	0d478793          	addi	a5,a5,212 # 100d4 <bitmap>
     da0:	00d787b3          	add	a5,a5,a3
     da4:	0007c783          	lbu	a5,0(a5)
     da8:	01879613          	slli	a2,a5,0x18
     dac:	41865613          	srai	a2,a2,0x18
     db0:	fe042703          	lw	a4,-32(s0)
     db4:	41f75793          	srai	a5,a4,0x1f
     db8:	01d7d793          	srli	a5,a5,0x1d
     dbc:	00f70733          	add	a4,a4,a5
     dc0:	00777713          	andi	a4,a4,7
     dc4:	40f707b3          	sub	a5,a4,a5
     dc8:	00078713          	mv	a4,a5
     dcc:	00100793          	li	a5,1
     dd0:	00e797b3          	sll	a5,a5,a4
     dd4:	01879793          	slli	a5,a5,0x18
     dd8:	4187d793          	srai	a5,a5,0x18
     ddc:	00f667b3          	or	a5,a2,a5
     de0:	01879793          	slli	a5,a5,0x18
     de4:	4187d793          	srai	a5,a5,0x18
     de8:	0ff7f713          	zext.b	a4,a5
     dec:	000107b7          	lui	a5,0x10
     df0:	0d478793          	addi	a5,a5,212 # 100d4 <bitmap>
     df4:	00d787b3          	add	a5,a5,a3
     df8:	00e78023          	sb	a4,0(a5)
                    ip->direct[blockIndex] = i + sb.dataStart;
     dfc:	000107b7          	lui	a5,0x10
     e00:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     e04:	0107a703          	lw	a4,16(a5)
     e08:	fe042783          	lw	a5,-32(s0)
     e0c:	00f70733          	add	a4,a4,a5
     e10:	fdc42683          	lw	a3,-36(s0)
     e14:	fd842783          	lw	a5,-40(s0)
     e18:	00279793          	slli	a5,a5,0x2
     e1c:	00f687b3          	add	a5,a3,a5
     e20:	00e7a223          	sw	a4,4(a5)
                    write_block(sb.bitmapStart, bitmap);
     e24:	000107b7          	lui	a5,0x10
     e28:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     e2c:	0087a703          	lw	a4,8(a5)
     e30:	000107b7          	lui	a5,0x10
     e34:	0d478593          	addi	a1,a5,212 # 100d4 <bitmap>
     e38:	00070513          	mv	a0,a4
     e3c:	cc0ff0ef          	jal	ra,2fc <write_block>
                    break;
     e40:	0340006f          	j	e74 <write+0x2c8>
            for (int i = 0; i < sb.blocksNumber - sb.dataStart; i++) {
     e44:	fe042783          	lw	a5,-32(s0)
     e48:	00178793          	addi	a5,a5,1
     e4c:	fef42023          	sw	a5,-32(s0)
     e50:	000107b7          	lui	a5,0x10
     e54:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     e58:	0047a703          	lw	a4,4(a5)
     e5c:	000107b7          	lui	a5,0x10
     e60:	0c078793          	addi	a5,a5,192 # 100c0 <sb>
     e64:	0107a783          	lw	a5,16(a5)
     e68:	40f707b3          	sub	a5,a4,a5
     e6c:	fe042703          	lw	a4,-32(s0)
     e70:	ecf740e3          	blt	a4,a5,d30 <write+0x184>
                }
            }
            if (ip->direct[blockIndex] == 0) break;
     e74:	fdc42703          	lw	a4,-36(s0)
     e78:	fd842783          	lw	a5,-40(s0)
     e7c:	00279793          	slli	a5,a5,0x2
     e80:	00f707b3          	add	a5,a4,a5
     e84:	0047a783          	lw	a5,4(a5)
     e88:	10078063          	beqz	a5,f88 <write+0x3dc>
        }

        read_block(ip->direct[blockIndex], block);
     e8c:	fdc42703          	lw	a4,-36(s0)
     e90:	fd842783          	lw	a5,-40(s0)
     e94:	00279793          	slli	a5,a5,0x2
     e98:	00f707b3          	add	a5,a4,a5
     e9c:	0047a783          	lw	a5,4(a5)
     ea0:	dd440713          	addi	a4,s0,-556
     ea4:	00070593          	mv	a1,a4
     ea8:	00078513          	mv	a0,a5
     eac:	bacff0ef          	jal	ra,258 <read_block>
        memcpy(block + offset, buf, chunk);
     eb0:	fd442783          	lw	a5,-44(s0)
     eb4:	dd440713          	addi	a4,s0,-556
     eb8:	00f707b3          	add	a5,a4,a5
     ebc:	fe442703          	lw	a4,-28(s0)
     ec0:	00070613          	mv	a2,a4
     ec4:	dc842583          	lw	a1,-568(s0)
     ec8:	00078513          	mv	a0,a5
     ecc:	284000ef          	jal	ra,1150 <memcpy>
        write_block(ip->direct[blockIndex], block);
     ed0:	fdc42703          	lw	a4,-36(s0)
     ed4:	fd842783          	lw	a5,-40(s0)
     ed8:	00279793          	slli	a5,a5,0x2
     edc:	00f707b3          	add	a5,a4,a5
     ee0:	0047a783          	lw	a5,4(a5)
     ee4:	dd440713          	addi	a4,s0,-556
     ee8:	00070593          	mv	a1,a4
     eec:	00078513          	mv	a0,a5
     ef0:	c0cff0ef          	jal	ra,2fc <write_block>

        buf = (char*)buf + chunk;
     ef4:	fe442783          	lw	a5,-28(s0)
     ef8:	dc842703          	lw	a4,-568(s0)
     efc:	00f707b3          	add	a5,a4,a5
     f00:	dcf42423          	sw	a5,-568(s0)
        done += chunk;
     f04:	fec42703          	lw	a4,-20(s0)
     f08:	fe442783          	lw	a5,-28(s0)
     f0c:	00f707b3          	add	a5,a4,a5
     f10:	fef42623          	sw	a5,-20(s0)
        left -= chunk;
     f14:	fe842703          	lw	a4,-24(s0)
     f18:	fe442783          	lw	a5,-28(s0)
     f1c:	40f707b3          	sub	a5,a4,a5
     f20:	fef42423          	sw	a5,-24(s0)
        fileTable[fd].pos += chunk;
     f24:	000107b7          	lui	a5,0x10
     f28:	00078693          	mv	a3,a5
     f2c:	dcc42703          	lw	a4,-564(s0)
     f30:	00070793          	mv	a5,a4
     f34:	00179793          	slli	a5,a5,0x1
     f38:	00e787b3          	add	a5,a5,a4
     f3c:	00279793          	slli	a5,a5,0x2
     f40:	00f687b3          	add	a5,a3,a5
     f44:	0007a703          	lw	a4,0(a5) # 10000 <fileTable>
     f48:	fe442783          	lw	a5,-28(s0)
     f4c:	00f706b3          	add	a3,a4,a5
     f50:	000107b7          	lui	a5,0x10
     f54:	00078613          	mv	a2,a5
     f58:	dcc42703          	lw	a4,-564(s0)
     f5c:	00070793          	mv	a5,a4
     f60:	00179793          	slli	a5,a5,0x1
     f64:	00e787b3          	add	a5,a5,a4
     f68:	00279793          	slli	a5,a5,0x2
     f6c:	00f607b3          	add	a5,a2,a5
     f70:	00d7a023          	sw	a3,0(a5) # 10000 <fileTable>
    while (left > 0) {
     f74:	fe842783          	lw	a5,-24(s0)
     f78:	cef04ae3          	bgtz	a5,c6c <write+0xc0>
     f7c:	0100006f          	j	f8c <write+0x3e0>
        if (blockIndex >= MAX_NUMBER_OF_BLOCKS_IN_FILE) break;
     f80:	00000013          	nop
     f84:	0080006f          	j	f8c <write+0x3e0>
            if (ip->direct[blockIndex] == 0) break;
     f88:	00000013          	nop
    }

    if (fileTable[fd].pos > ip->size)
     f8c:	000107b7          	lui	a5,0x10
     f90:	00078693          	mv	a3,a5
     f94:	dcc42703          	lw	a4,-564(s0)
     f98:	00070793          	mv	a5,a4
     f9c:	00179793          	slli	a5,a5,0x1
     fa0:	00e787b3          	add	a5,a5,a4
     fa4:	00279793          	slli	a5,a5,0x2
     fa8:	00f687b3          	add	a5,a3,a5
     fac:	0007a703          	lw	a4,0(a5) # 10000 <fileTable>
     fb0:	fdc42783          	lw	a5,-36(s0)
     fb4:	0007a783          	lw	a5,0(a5)
     fb8:	02e7d863          	bge	a5,a4,fe8 <write+0x43c>
        ip->size = fileTable[fd].pos;
     fbc:	000107b7          	lui	a5,0x10
     fc0:	00078693          	mv	a3,a5
     fc4:	dcc42703          	lw	a4,-564(s0)
     fc8:	00070793          	mv	a5,a4
     fcc:	00179793          	slli	a5,a5,0x1
     fd0:	00e787b3          	add	a5,a5,a4
     fd4:	00279793          	slli	a5,a5,0x2
     fd8:	00f687b3          	add	a5,a3,a5
     fdc:	0007a703          	lw	a4,0(a5) # 10000 <fileTable>
     fe0:	fdc42783          	lw	a5,-36(s0)
     fe4:	00e7a023          	sw	a4,0(a5)
    flush_inode(fileTable[fd].inum);
     fe8:	000107b7          	lui	a5,0x10
     fec:	00078693          	mv	a3,a5
     ff0:	dcc42703          	lw	a4,-564(s0)
     ff4:	00070793          	mv	a5,a4
     ff8:	00179793          	slli	a5,a5,0x1
     ffc:	00e787b3          	add	a5,a5,a4
    1000:	00279793          	slli	a5,a5,0x2
    1004:	00f687b3          	add	a5,a3,a5
    1008:	0047a783          	lw	a5,4(a5) # 10004 <fileTable+0x4>
    100c:	00078513          	mv	a0,a5
    1010:	ac9ff0ef          	jal	ra,ad8 <flush_inode>

    return done;
    1014:	fec42783          	lw	a5,-20(s0)
}
    1018:	00078513          	mv	a0,a5
    101c:	23c12083          	lw	ra,572(sp)
    1020:	23812403          	lw	s0,568(sp)
    1024:	24010113          	addi	sp,sp,576
    1028:	00008067          	ret

0000102c <strlen>:
int strlen(const char *s) {
    102c:	fd010113          	addi	sp,sp,-48
    1030:	02812623          	sw	s0,44(sp)
    1034:	03010413          	addi	s0,sp,48
    1038:	fca42e23          	sw	a0,-36(s0)
    int len = 0;
    103c:	fe042623          	sw	zero,-20(s0)
    while (*s++) len++;
    1040:	0100006f          	j	1050 <strlen+0x24>
    1044:	fec42783          	lw	a5,-20(s0)
    1048:	00178793          	addi	a5,a5,1
    104c:	fef42623          	sw	a5,-20(s0)
    1050:	fdc42783          	lw	a5,-36(s0)
    1054:	00178713          	addi	a4,a5,1
    1058:	fce42e23          	sw	a4,-36(s0)
    105c:	0007c783          	lbu	a5,0(a5)
    1060:	fe0792e3          	bnez	a5,1044 <strlen+0x18>
    return len;
    1064:	fec42783          	lw	a5,-20(s0)
}
    1068:	00078513          	mv	a0,a5
    106c:	02c12403          	lw	s0,44(sp)
    1070:	03010113          	addi	sp,sp,48
    1074:	00008067          	ret

00001078 <strcmp>:

int strcmp(const char *a, const char *b) {
    1078:	fe010113          	addi	sp,sp,-32
    107c:	00812e23          	sw	s0,28(sp)
    1080:	02010413          	addi	s0,sp,32
    1084:	fea42623          	sw	a0,-20(s0)
    1088:	feb42423          	sw	a1,-24(s0)
    while (*a && (*a == *b)) {
    108c:	01c0006f          	j	10a8 <strcmp+0x30>
        a++; b++;
    1090:	fec42783          	lw	a5,-20(s0)
    1094:	00178793          	addi	a5,a5,1
    1098:	fef42623          	sw	a5,-20(s0)
    109c:	fe842783          	lw	a5,-24(s0)
    10a0:	00178793          	addi	a5,a5,1
    10a4:	fef42423          	sw	a5,-24(s0)
    while (*a && (*a == *b)) {
    10a8:	fec42783          	lw	a5,-20(s0)
    10ac:	0007c783          	lbu	a5,0(a5)
    10b0:	00078c63          	beqz	a5,10c8 <strcmp+0x50>
    10b4:	fec42783          	lw	a5,-20(s0)
    10b8:	0007c703          	lbu	a4,0(a5)
    10bc:	fe842783          	lw	a5,-24(s0)
    10c0:	0007c783          	lbu	a5,0(a5)
    10c4:	fcf706e3          	beq	a4,a5,1090 <strcmp+0x18>
    }
    return *(unsigned char *)a - *(unsigned char *)b;
    10c8:	fec42783          	lw	a5,-20(s0)
    10cc:	0007c783          	lbu	a5,0(a5)
    10d0:	00078713          	mv	a4,a5
    10d4:	fe842783          	lw	a5,-24(s0)
    10d8:	0007c783          	lbu	a5,0(a5)
    10dc:	40f707b3          	sub	a5,a4,a5
}
    10e0:	00078513          	mv	a0,a5
    10e4:	01c12403          	lw	s0,28(sp)
    10e8:	02010113          	addi	sp,sp,32
    10ec:	00008067          	ret

000010f0 <memset>:

void *memset(void *s, int c, unsigned int n) {
    10f0:	fd010113          	addi	sp,sp,-48
    10f4:	02812623          	sw	s0,44(sp)
    10f8:	03010413          	addi	s0,sp,48
    10fc:	fca42e23          	sw	a0,-36(s0)
    1100:	fcb42c23          	sw	a1,-40(s0)
    1104:	fcc42a23          	sw	a2,-44(s0)
    unsigned char *p = s;
    1108:	fdc42783          	lw	a5,-36(s0)
    110c:	fef42623          	sw	a5,-20(s0)
    while (n--) *p++ = (unsigned char)c;
    1110:	01c0006f          	j	112c <memset+0x3c>
    1114:	fec42783          	lw	a5,-20(s0)
    1118:	00178713          	addi	a4,a5,1
    111c:	fee42623          	sw	a4,-20(s0)
    1120:	fd842703          	lw	a4,-40(s0)
    1124:	0ff77713          	zext.b	a4,a4
    1128:	00e78023          	sb	a4,0(a5)
    112c:	fd442783          	lw	a5,-44(s0)
    1130:	fff78713          	addi	a4,a5,-1
    1134:	fce42a23          	sw	a4,-44(s0)
    1138:	fc079ee3          	bnez	a5,1114 <memset+0x24>
    return s;
    113c:	fdc42783          	lw	a5,-36(s0)
}
    1140:	00078513          	mv	a0,a5
    1144:	02c12403          	lw	s0,44(sp)
    1148:	03010113          	addi	sp,sp,48
    114c:	00008067          	ret

00001150 <memcpy>:

void *memcpy(void *dest, const void *src, unsigned int n) {
    1150:	fd010113          	addi	sp,sp,-48
    1154:	02812623          	sw	s0,44(sp)
    1158:	03010413          	addi	s0,sp,48
    115c:	fca42e23          	sw	a0,-36(s0)
    1160:	fcb42c23          	sw	a1,-40(s0)
    1164:	fcc42a23          	sw	a2,-44(s0)
    char *d = dest;
    1168:	fdc42783          	lw	a5,-36(s0)
    116c:	fef42623          	sw	a5,-20(s0)
    const char *s = src;
    1170:	fd842783          	lw	a5,-40(s0)
    1174:	fef42423          	sw	a5,-24(s0)
    while (n--) *d++ = *s++;
    1178:	0240006f          	j	119c <memcpy+0x4c>
    117c:	fe842703          	lw	a4,-24(s0)
    1180:	00170793          	addi	a5,a4,1
    1184:	fef42423          	sw	a5,-24(s0)
    1188:	fec42783          	lw	a5,-20(s0)
    118c:	00178693          	addi	a3,a5,1
    1190:	fed42623          	sw	a3,-20(s0)
    1194:	00074703          	lbu	a4,0(a4)
    1198:	00e78023          	sb	a4,0(a5)
    119c:	fd442783          	lw	a5,-44(s0)
    11a0:	fff78713          	addi	a4,a5,-1
    11a4:	fce42a23          	sw	a4,-44(s0)
    11a8:	fc079ae3          	bnez	a5,117c <memcpy+0x2c>
    return dest;
    11ac:	fdc42783          	lw	a5,-36(s0)
}
    11b0:	00078513          	mv	a0,a5
    11b4:	02c12403          	lw	s0,44(sp)
    11b8:	03010113          	addi	sp,sp,48
    11bc:	00008067          	ret

000011c0 <strncpy>:

char *strncpy(char *dest, const char *src, int n) {
    11c0:	fd010113          	addi	sp,sp,-48
    11c4:	02812623          	sw	s0,44(sp)
    11c8:	03010413          	addi	s0,sp,48
    11cc:	fca42e23          	sw	a0,-36(s0)
    11d0:	fcb42c23          	sw	a1,-40(s0)
    11d4:	fcc42a23          	sw	a2,-44(s0)
    int i;
    for (i = 0; i < n && src[i]; i++) dest[i] = src[i];
    11d8:	fe042623          	sw	zero,-20(s0)
    11dc:	0300006f          	j	120c <strncpy+0x4c>
    11e0:	fec42783          	lw	a5,-20(s0)
    11e4:	fd842703          	lw	a4,-40(s0)
    11e8:	00f70733          	add	a4,a4,a5
    11ec:	fec42783          	lw	a5,-20(s0)
    11f0:	fdc42683          	lw	a3,-36(s0)
    11f4:	00f687b3          	add	a5,a3,a5
    11f8:	00074703          	lbu	a4,0(a4)
    11fc:	00e78023          	sb	a4,0(a5)
    1200:	fec42783          	lw	a5,-20(s0)
    1204:	00178793          	addi	a5,a5,1
    1208:	fef42623          	sw	a5,-20(s0)
    120c:	fec42703          	lw	a4,-20(s0)
    1210:	fd442783          	lw	a5,-44(s0)
    1214:	02f75c63          	bge	a4,a5,124c <strncpy+0x8c>
    1218:	fec42783          	lw	a5,-20(s0)
    121c:	fd842703          	lw	a4,-40(s0)
    1220:	00f707b3          	add	a5,a4,a5
    1224:	0007c783          	lbu	a5,0(a5)
    1228:	fa079ce3          	bnez	a5,11e0 <strncpy+0x20>
    for (; i < n; i++) dest[i] = '\0';
    122c:	0200006f          	j	124c <strncpy+0x8c>
    1230:	fec42783          	lw	a5,-20(s0)
    1234:	fdc42703          	lw	a4,-36(s0)
    1238:	00f707b3          	add	a5,a4,a5
    123c:	00078023          	sb	zero,0(a5)
    1240:	fec42783          	lw	a5,-20(s0)
    1244:	00178793          	addi	a5,a5,1
    1248:	fef42623          	sw	a5,-20(s0)
    124c:	fec42703          	lw	a4,-20(s0)
    1250:	fd442783          	lw	a5,-44(s0)
    1254:	fcf74ee3          	blt	a4,a5,1230 <strncpy+0x70>
    return dest;
    1258:	fdc42783          	lw	a5,-36(s0)
}
    125c:	00078513          	mv	a0,a5
    1260:	02c12403          	lw	s0,44(sp)
    1264:	03010113          	addi	sp,sp,48
    1268:	00008067          	ret
