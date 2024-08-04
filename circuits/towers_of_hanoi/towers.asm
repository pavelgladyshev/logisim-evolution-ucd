
towers:     file format elf32-littleriscv


Disassembly of section .init:

00400000 <_start>:
    .extern __stack_init      # address of the initial top of C call stack (calculated externally 
                              # by linker)

	.globl _start
_start:                       # this is where CPU starts executing instructions after reset / power-on
	la sp,__stack_init        # initialise sp (with the value that points to the last word of RAM)
  400000:	83c00117          	auipc	sp,0x83c00
  400004:	ffc10113          	addi	sp,sp,-4 # 83fffffc <__stack_init>
	li a0,0                   # populate optional main() parameters with dummy values (just in case)
  400008:	00000513          	li	a0,0
	li a1,0
  40000c:	00000593          	li	a1,0
	li a2,0
  400010:	00000613          	li	a2,0
	jal main                  # call C main() function
  400014:	348000ef          	jal	40035c <main>

00400018 <exit>:
exit:
	j exit                    # keep looping forever after main() returns
  400018:	0000006f          	j	400018 <exit>

Disassembly of section .text:

0040001c <printstr>:

void printstr(char str[]) 
{
    int i;

    for(i = 0; str[i] != '\0'; i++) 
  40001c:	00054783          	lbu	a5,0(a0)
  400020:	02078063          	beqz	a5,400040 <printstr+0x24>
  400024:	00150513          	addi	a0,a0,1
    {
       TDR = str[i];
  400028:	ffff0737          	lui	a4,0xffff0
  40002c:	00c70713          	addi	a4,a4,12 # ffff000c <__stack_init+0x7bff0010>
  400030:	00f70023          	sb	a5,0(a4)
    for(i = 0; str[i] != '\0'; i++) 
  400034:	00150513          	addi	a0,a0,1
  400038:	fff54783          	lbu	a5,-1(a0)
  40003c:	fe079ae3          	bnez	a5,400030 <printstr+0x14>
    }
    return;
}
  400040:	00008067          	ret

00400044 <printhex>:

void printhex(int x)
{
  400044:	fe010113          	addi	sp,sp,-32
  400048:	00112e23          	sw	ra,28(sp)
  char str[9];
  int i;
  for (i = 0; i < 8; i++)
  40004c:	00410713          	addi	a4,sp,4
  400050:	ffc10593          	addi	a1,sp,-4
  {
    str[7-i] = (x & 0xF) + ((x & 0xF) < 10 ? '0' : 'a'-10);
  400054:	00900613          	li	a2,9
  400058:	00f57693          	andi	a3,a0,15
  40005c:	00d627b3          	slt	a5,a2,a3
  400060:	40f007b3          	neg	a5,a5
  400064:	0277f793          	andi	a5,a5,39
  400068:	03078793          	addi	a5,a5,48
  40006c:	00f687b3          	add	a5,a3,a5
  400070:	00f703a3          	sb	a5,7(a4)
    x >>= 4;
  400074:	40455513          	srai	a0,a0,0x4
  for (i = 0; i < 8; i++)
  400078:	fff70713          	addi	a4,a4,-1
  40007c:	fcb71ee3          	bne	a4,a1,400058 <printhex+0x14>
  }
  str[8] = 0;
  400080:	00010623          	sb	zero,12(sp)

  printstr(str);
  400084:	00410513          	addi	a0,sp,4
  400088:	f95ff0ef          	jal	40001c <printstr>
}
  40008c:	01c12083          	lw	ra,28(sp)
  400090:	02010113          	addi	sp,sp,32
  400094:	00008067          	ret

00400098 <list_getSize>:
struct Node g_nodePool[NUM_DISCS];

int list_getSize( struct List* list )
{
  return list->size;
}
  400098:	00052503          	lw	a0,0(a0)
  40009c:	00008067          	ret

004000a0 <list_init>:

void list_init( struct List* list )
{
  list->size = 0;
  4000a0:	00052023          	sw	zero,0(a0)
  list->head = 0;
  4000a4:	00052223          	sw	zero,4(a0)
}
  4000a8:	00008067          	ret

004000ac <list_push>:
void list_push( struct List* list, int val )
{
  struct Node* newNode;

  // Pop the next free node off the free list
  newNode = g_nodeFreeList.head;
  4000ac:	7fc00717          	auipc	a4,0x7fc00
  4000b0:	f7c70713          	addi	a4,a4,-132 # 80000028 <g_nodeFreeList>
  4000b4:	00472783          	lw	a5,4(a4)
  g_nodeFreeList.head = g_nodeFreeList.head->next;
  4000b8:	0047a683          	lw	a3,4(a5)
  4000bc:	00d72223          	sw	a3,4(a4)

  // Push the new node onto the given list
  newNode->next = list->head;
  4000c0:	00452703          	lw	a4,4(a0)
  4000c4:	00e7a223          	sw	a4,4(a5)
  list->head = newNode;
  4000c8:	00f52223          	sw	a5,4(a0)

  // Assign the value
  list->head->val = val;
  4000cc:	00b7a023          	sw	a1,0(a5)

  // Increment size
  list->size++;
  4000d0:	00052783          	lw	a5,0(a0)
  4000d4:	00178793          	addi	a5,a5,1
  4000d8:	00f52023          	sw	a5,0(a0)

}
  4000dc:	00008067          	ret

004000e0 <list_pop>:

int list_pop( struct List* list )
{
  4000e0:	00050793          	mv	a5,a0
  struct Node* freedNode;
  int val;

  // Get the value from the->head of given list
  val = list->head->val;
  4000e4:	00452703          	lw	a4,4(a0)
  4000e8:	00072503          	lw	a0,0(a4)

  // Pop the head node off the given list
  freedNode = list->head;
  list->head = list->head->next;
  4000ec:	00472683          	lw	a3,4(a4)
  4000f0:	00d7a223          	sw	a3,4(a5)

  // Push the freed node onto the free list
  freedNode->next = g_nodeFreeList.head;
  4000f4:	7fc00697          	auipc	a3,0x7fc00
  4000f8:	f3468693          	addi	a3,a3,-204 # 80000028 <g_nodeFreeList>
  4000fc:	0046a603          	lw	a2,4(a3)
  400100:	00c72223          	sw	a2,4(a4)
  g_nodeFreeList.head = freedNode;
  400104:	00e6a223          	sw	a4,4(a3)

  // Decrement size
  list->size--;
  400108:	0007a703          	lw	a4,0(a5)
  40010c:	fff70713          	addi	a4,a4,-1
  400110:	00e7a023          	sw	a4,0(a5)

  return val;
}
  400114:	00008067          	ret

00400118 <list_clear>:

void list_clear( struct List* list )
{
  400118:	ff010113          	addi	sp,sp,-16
  40011c:	00112623          	sw	ra,12(sp)
  400120:	00812423          	sw	s0,8(sp)
  400124:	00050413          	mv	s0,a0
  while ( list_getSize(list) > 0 )
  400128:	00052783          	lw	a5,0(a0)
  40012c:	00f05a63          	blez	a5,400140 <list_clear+0x28>
    list_pop(list);
  400130:	00040513          	mv	a0,s0
  400134:	fadff0ef          	jal	4000e0 <list_pop>
  while ( list_getSize(list) > 0 )
  400138:	00042783          	lw	a5,0(s0)
  40013c:	fef04ae3          	bgtz	a5,400130 <list_clear+0x18>
}
  400140:	00c12083          	lw	ra,12(sp)
  400144:	00812403          	lw	s0,8(sp)
  400148:	01010113          	addi	sp,sp,16
  40014c:	00008067          	ret

00400150 <towers_init>:
  struct List pegB;
  struct List pegC;
};

void towers_init( struct Towers* this, int n )
{
  400150:	ff010113          	addi	sp,sp,-16
  400154:	00112623          	sw	ra,12(sp)
  400158:	00812423          	sw	s0,8(sp)
  40015c:	00912223          	sw	s1,4(sp)
  400160:	00058413          	mv	s0,a1
  int i;

  this->numDiscs = n;
  400164:	00b52023          	sw	a1,0(a0)
  this->numMoves = 0;
  400168:	00052223          	sw	zero,4(a0)

  list_init( &(this->pegA) );
  40016c:	00850493          	addi	s1,a0,8
  list->size = 0;
  400170:	00052423          	sw	zero,8(a0)
  list->head = 0;
  400174:	00052623          	sw	zero,12(a0)
  list->size = 0;
  400178:	00052823          	sw	zero,16(a0)
  list->head = 0;
  40017c:	00052a23          	sw	zero,20(a0)
  list->size = 0;
  400180:	00052c23          	sw	zero,24(a0)
  list->head = 0;
  400184:	00052e23          	sw	zero,28(a0)
  list_init( &(this->pegB) );
  list_init( &(this->pegC) );

  for ( i = 0; i < n; i++ )
  400188:	00b05c63          	blez	a1,4001a0 <towers_init+0x50>
  {
    list_push( &(this->pegA), n-i );
  40018c:	00040593          	mv	a1,s0
  400190:	00048513          	mv	a0,s1
  400194:	f19ff0ef          	jal	4000ac <list_push>
  for ( i = 0; i < n; i++ )
  400198:	fff40413          	addi	s0,s0,-1
  40019c:	fe0418e3          	bnez	s0,40018c <towers_init+0x3c>
  }
}
  4001a0:	00c12083          	lw	ra,12(sp)
  4001a4:	00812403          	lw	s0,8(sp)
  4001a8:	00412483          	lw	s1,4(sp)
  4001ac:	01010113          	addi	sp,sp,16
  4001b0:	00008067          	ret

004001b4 <towers_clear>:

void towers_clear( struct Towers* this )
{
  4001b4:	ff010113          	addi	sp,sp,-16
  4001b8:	00112623          	sw	ra,12(sp)
  4001bc:	00812423          	sw	s0,8(sp)
  4001c0:	00050413          	mv	s0,a0

  list_clear( &(this->pegA) );
  4001c4:	00850513          	addi	a0,a0,8
  4001c8:	f51ff0ef          	jal	400118 <list_clear>
  list_clear( &(this->pegB) );
  4001cc:	01040513          	addi	a0,s0,16
  4001d0:	f49ff0ef          	jal	400118 <list_clear>
  list_clear( &(this->pegC) );
  4001d4:	01840513          	addi	a0,s0,24
  4001d8:	f41ff0ef          	jal	400118 <list_clear>

  towers_init( this, this->numDiscs );
  4001dc:	00042583          	lw	a1,0(s0)
  4001e0:	00040513          	mv	a0,s0
  4001e4:	f6dff0ef          	jal	400150 <towers_init>

}
  4001e8:	00c12083          	lw	ra,12(sp)
  4001ec:	00812403          	lw	s0,8(sp)
  4001f0:	01010113          	addi	sp,sp,16
  4001f4:	00008067          	ret

004001f8 <towers_solve_h>:

void towers_solve_h( struct Towers* this, int n,
                     struct List* startPeg,
                     struct List* tempPeg,
                     struct List* destPeg )
{
  4001f8:	fe010113          	addi	sp,sp,-32
  4001fc:	00112e23          	sw	ra,28(sp)
  400200:	00912a23          	sw	s1,20(sp)
  400204:	01212823          	sw	s2,16(sp)
  400208:	01312623          	sw	s3,12(sp)
  40020c:	00050493          	mv	s1,a0
  400210:	00060913          	mv	s2,a2
  400214:	00070993          	mv	s3,a4
  int val;

  if ( n == 1 ) {
  400218:	00100793          	li	a5,1
  40021c:	06f58a63          	beq	a1,a5,400290 <towers_solve_h+0x98>
  400220:	00812c23          	sw	s0,24(sp)
  400224:	01412423          	sw	s4,8(sp)
  400228:	00068a13          	mv	s4,a3
    val = list_pop(startPeg);
    list_push(destPeg,val);
    this->numMoves++;
  }
  else {
    towers_solve_h( this, n-1, startPeg, destPeg,  tempPeg );
  40022c:	fff58413          	addi	s0,a1,-1
  400230:	00068713          	mv	a4,a3
  400234:	00098693          	mv	a3,s3
  400238:	00040593          	mv	a1,s0
  40023c:	fbdff0ef          	jal	4001f8 <towers_solve_h>
    towers_solve_h( this, 1,   startPeg, tempPeg,  destPeg );
  400240:	00098713          	mv	a4,s3
  400244:	000a0693          	mv	a3,s4
  400248:	00090613          	mv	a2,s2
  40024c:	00100593          	li	a1,1
  400250:	00048513          	mv	a0,s1
  400254:	fa5ff0ef          	jal	4001f8 <towers_solve_h>
    towers_solve_h( this, n-1, tempPeg,  startPeg, destPeg );
  400258:	00098713          	mv	a4,s3
  40025c:	00090693          	mv	a3,s2
  400260:	000a0613          	mv	a2,s4
  400264:	00040593          	mv	a1,s0
  400268:	00048513          	mv	a0,s1
  40026c:	f8dff0ef          	jal	4001f8 <towers_solve_h>
  400270:	01812403          	lw	s0,24(sp)
  400274:	00812a03          	lw	s4,8(sp)
  }

}
  400278:	01c12083          	lw	ra,28(sp)
  40027c:	01412483          	lw	s1,20(sp)
  400280:	01012903          	lw	s2,16(sp)
  400284:	00c12983          	lw	s3,12(sp)
  400288:	02010113          	addi	sp,sp,32
  40028c:	00008067          	ret
    val = list_pop(startPeg);
  400290:	00060513          	mv	a0,a2
  400294:	e4dff0ef          	jal	4000e0 <list_pop>
  400298:	00050593          	mv	a1,a0
    list_push(destPeg,val);
  40029c:	00098513          	mv	a0,s3
  4002a0:	e0dff0ef          	jal	4000ac <list_push>
    this->numMoves++;
  4002a4:	0044a783          	lw	a5,4(s1)
  4002a8:	00178793          	addi	a5,a5,1
  4002ac:	00f4a223          	sw	a5,4(s1)
  4002b0:	fc9ff06f          	j	400278 <towers_solve_h+0x80>

004002b4 <towers_solve>:

void towers_solve( struct Towers* this )
{
  4002b4:	ff010113          	addi	sp,sp,-16
  4002b8:	00112623          	sw	ra,12(sp)
  towers_solve_h( this, this->numDiscs, &(this->pegA), &(this->pegB), &(this->pegC) );
  4002bc:	01850713          	addi	a4,a0,24
  4002c0:	01050693          	addi	a3,a0,16
  4002c4:	00850613          	addi	a2,a0,8
  4002c8:	00052583          	lw	a1,0(a0)
  4002cc:	f2dff0ef          	jal	4001f8 <towers_solve_h>
}
  4002d0:	00c12083          	lw	ra,12(sp)
  4002d4:	01010113          	addi	sp,sp,16
  4002d8:	00008067          	ret

004002dc <towers_verify>:

int towers_verify( struct Towers* this )
{
  4002dc:	00050613          	mv	a2,a0
  struct Node* ptr;
  int numDiscs = 0;

  if ( list_getSize(&this->pegA) != 0 ) {
  4002e0:	00852783          	lw	a5,8(a0)
  4002e4:	04079863          	bnez	a5,400334 <towers_verify+0x58>
  return list->size;
  4002e8:	01052503          	lw	a0,16(a0)
    return 2;
  }

  if ( list_getSize(&this->pegB) != 0 ) {
  4002ec:	04051863          	bnez	a0,40033c <towers_verify+0x60>
    return 3;
  }

  if ( list_getSize(&this->pegC) != this->numDiscs ) {
  4002f0:	00062583          	lw	a1,0(a2)
  4002f4:	01862783          	lw	a5,24(a2)
  4002f8:	04f59663          	bne	a1,a5,400344 <towers_verify+0x68>
    return 4;
  }

  for ( ptr = this->pegC.head; ptr != 0; ptr = ptr->next ) {
  4002fc:	01c62783          	lw	a5,28(a2)
  400300:	00078e63          	beqz	a5,40031c <towers_verify+0x40>
  int numDiscs = 0;
  400304:	00050713          	mv	a4,a0
    numDiscs++;
  400308:	00170713          	addi	a4,a4,1
    if ( ptr->val != numDiscs ) {
  40030c:	0007a683          	lw	a3,0(a5)
  400310:	02e69e63          	bne	a3,a4,40034c <towers_verify+0x70>
  for ( ptr = this->pegC.head; ptr != 0; ptr = ptr->next ) {
  400314:	0047a783          	lw	a5,4(a5)
  400318:	fe0798e3          	bnez	a5,400308 <towers_verify+0x2c>
      return 5;
    }
  }

  if ( this->numMoves != ((1 << this->numDiscs) - 1) ) {
  40031c:	00100793          	li	a5,1
  400320:	00b797b3          	sll	a5,a5,a1
  400324:	fff78793          	addi	a5,a5,-1
  400328:	00462703          	lw	a4,4(a2)
  40032c:	02f71463          	bne	a4,a5,400354 <towers_verify+0x78>
    return 6;
  }

  return 0;
}
  400330:	00008067          	ret
    return 2;
  400334:	00200513          	li	a0,2
  400338:	00008067          	ret
    return 3;
  40033c:	00300513          	li	a0,3
  400340:	00008067          	ret
    return 4;
  400344:	00400513          	li	a0,4
  400348:	00008067          	ret
      return 5;
  40034c:	00500513          	li	a0,5
  400350:	00008067          	ret
    return 6;
  400354:	00600513          	li	a0,6
  400358:	00008067          	ret

0040035c <main>:

//--------------------------------------------------------------------------
// Main

int main( int argc, char* argv[] )
{
  40035c:	fd010113          	addi	sp,sp,-48
  400360:	02112623          	sw	ra,44(sp)
  400364:	02812423          	sw	s0,40(sp)
  400368:	02912223          	sw	s1,36(sp)
  struct Towers towers;
  int i;
  int res;

  printstr("Towers of Hanoi\n");
  40036c:	00000517          	auipc	a0,0x0
  400370:	12c50513          	addi	a0,a0,300 # 400498 <main+0x13c>
  400374:	ca9ff0ef          	jal	40001c <printstr>

  printstr("NUM_DISCS = "); printhex(NUM_DISCS); printstr("\n"); 
  400378:	00000517          	auipc	a0,0x0
  40037c:	13450513          	addi	a0,a0,308 # 4004ac <main+0x150>
  400380:	c9dff0ef          	jal	40001c <printstr>
  400384:	00500513          	li	a0,5
  400388:	cbdff0ef          	jal	400044 <printhex>
  40038c:	00000517          	auipc	a0,0x0
  400390:	13050513          	addi	a0,a0,304 # 4004bc <main+0x160>
  400394:	c89ff0ef          	jal	40001c <printstr>
  
  // Initialize free list

  list_init( &g_nodeFreeList );

  g_nodeFreeList.head = &(g_nodePool[0]);
  400398:	7fc00717          	auipc	a4,0x7fc00
  40039c:	c9070713          	addi	a4,a4,-880 # 80000028 <g_nodeFreeList>
  4003a0:	7fc00797          	auipc	a5,0x7fc00
  4003a4:	c6078793          	addi	a5,a5,-928 # 80000000 <g_nodePool>
  4003a8:	00f72223          	sw	a5,4(a4)
  g_nodeFreeList.size = NUM_DISCS;
  4003ac:	00500693          	li	a3,5
  4003b0:	00d72023          	sw	a3,0(a4)
  g_nodePool[NUM_DISCS-1].next = 0;
  4003b4:	0207a223          	sw	zero,36(a5)
  g_nodePool[NUM_DISCS-1].val = 99;
  4003b8:	06300713          	li	a4,99
  4003bc:	02e7a023          	sw	a4,32(a5)
  for ( i = 0; i < (NUM_DISCS-1); i++ ) {
    g_nodePool[i].next = &(g_nodePool[i+1]);
  4003c0:	7fc00717          	auipc	a4,0x7fc00
  4003c4:	c4870713          	addi	a4,a4,-952 # 80000008 <g_nodePool+0x8>
  4003c8:	00e7a223          	sw	a4,4(a5)
    g_nodePool[i].val = i;
  4003cc:	0007a023          	sw	zero,0(a5)
    g_nodePool[i].next = &(g_nodePool[i+1]);
  4003d0:	7fc00717          	auipc	a4,0x7fc00
  4003d4:	c4070713          	addi	a4,a4,-960 # 80000010 <g_nodePool+0x10>
  4003d8:	00e7a623          	sw	a4,12(a5)
    g_nodePool[i].val = i;
  4003dc:	00100713          	li	a4,1
  4003e0:	00e7a423          	sw	a4,8(a5)
    g_nodePool[i].next = &(g_nodePool[i+1]);
  4003e4:	7fc00717          	auipc	a4,0x7fc00
  4003e8:	c3470713          	addi	a4,a4,-972 # 80000018 <g_nodePool+0x18>
  4003ec:	00e7aa23          	sw	a4,20(a5)
    g_nodePool[i].val = i;
  4003f0:	00200713          	li	a4,2
  4003f4:	00e7a823          	sw	a4,16(a5)
    g_nodePool[i].next = &(g_nodePool[i+1]);
  4003f8:	7fc00717          	auipc	a4,0x7fc00
  4003fc:	c2870713          	addi	a4,a4,-984 # 80000020 <g_nodePool+0x20>
  400400:	00e7ae23          	sw	a4,28(a5)
    g_nodePool[i].val = i;
  400404:	00300713          	li	a4,3
  400408:	00e7ac23          	sw	a4,24(a5)
  }

  towers_init( &towers, NUM_DISCS );
  40040c:	00500593          	li	a1,5
  400410:	00010513          	mv	a0,sp
  400414:	d3dff0ef          	jal	400150 <towers_init>
  towers_solve( &towers );
#endif

  // Solve it

  towers_clear( &towers );
  400418:	00010513          	mv	a0,sp
  40041c:	d99ff0ef          	jal	4001b4 <towers_clear>
  //setStats(1);
  towers_solve( &towers );
  400420:	00010513          	mv	a0,sp
  400424:	e91ff0ef          	jal	4002b4 <towers_solve>
  //setStats(0);
  
  // Chek the results
  res = towers_verify( &towers );
  400428:	00010513          	mv	a0,sp
  40042c:	eb1ff0ef          	jal	4002dc <towers_verify>
  400430:	00050413          	mv	s0,a0
  printstr("Result = "); printhex(res); printstr("\n");
  400434:	00000517          	auipc	a0,0x0
  400438:	08c50513          	addi	a0,a0,140 # 4004c0 <main+0x164>
  40043c:	be1ff0ef          	jal	40001c <printstr>
  400440:	00040513          	mv	a0,s0
  400444:	c01ff0ef          	jal	400044 <printhex>
  400448:	00000517          	auipc	a0,0x0
  40044c:	07450513          	addi	a0,a0,116 # 4004bc <main+0x160>
  400450:	bcdff0ef          	jal	40001c <printstr>
 
  unsigned int count = COUNTER;
  400454:	ffff07b7          	lui	a5,0xffff0
  400458:	0207a483          	lw	s1,32(a5) # ffff0020 <__stack_init+0x7bff0024>
  printstr("Total Clock Ticks = "); printhex(count); printstr("\n"); 
  40045c:	00000517          	auipc	a0,0x0
  400460:	07050513          	addi	a0,a0,112 # 4004cc <main+0x170>
  400464:	bb9ff0ef          	jal	40001c <printstr>
  400468:	00048513          	mv	a0,s1
  40046c:	bd9ff0ef          	jal	400044 <printhex>
  400470:	00000517          	auipc	a0,0x0
  400474:	04c50513          	addi	a0,a0,76 # 4004bc <main+0x160>
  400478:	ba5ff0ef          	jal	40001c <printstr>

  asm("ebreak");
  40047c:	00100073          	ebreak
  return res;
}
  400480:	00040513          	mv	a0,s0
  400484:	02c12083          	lw	ra,44(sp)
  400488:	02812403          	lw	s0,40(sp)
  40048c:	02412483          	lw	s1,36(sp)
  400490:	03010113          	addi	sp,sp,48
  400494:	00008067          	ret
