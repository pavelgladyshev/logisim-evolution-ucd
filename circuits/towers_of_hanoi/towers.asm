
towers:     file format elf32-littleriscv


Disassembly of section .init:

00400000 <_start>:
    .extern __stack_init      # address of the initial top of C call stack (calculated externally 
                              # by linker)

	.globl _start
_start:                       # this is where CPU starts executing instructions after reset / power-on
	la sp,__stack_init        # initialise sp (with the value that points to the last word of RAM)
  400000:	83c00117          	auipc	sp,0x83c00
  400004:	ffc10113          	addi	sp,sp,-4 # 83fffffc <__stack_init+0x0>
	li a0,0                   # populate optional main() parameters with dummy values (just in case)
  400008:	00000513          	li	a0,0
	li a1,0
  40000c:	00000593          	li	a1,0
	li a2,0
  400010:	00000613          	li	a2,0
	jal main                  # call C main() function
  400014:	34c000ef          	jal	ra,400360 <main>

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
  400020:	00078e63          	beqz	a5,40003c <printstr+0x20>
  400024:	00150513          	addi	a0,a0,1
    {
       TDR = str[i];
  400028:	ffff0737          	lui	a4,0xffff0
  40002c:	00f70623          	sb	a5,12(a4) # ffff000c <__stack_init+0x7bff0010>
    for(i = 0; str[i] != '\0'; i++) 
  400030:	00150513          	addi	a0,a0,1
  400034:	fff54783          	lbu	a5,-1(a0)
  400038:	fe079ae3          	bnez	a5,40002c <printstr+0x10>
    }
    return;
}
  40003c:	00008067          	ret

00400040 <printhex>:

void printhex(int x)
{
  400040:	fe010113          	addi	sp,sp,-32
  400044:	00112e23          	sw	ra,28(sp)
  char str[9];
  int i;
  for (i = 0; i < 8; i++)
  400048:	00410793          	addi	a5,sp,4
  40004c:	ffc10893          	addi	a7,sp,-4
  {
    str[7-i] = (x & 0xF) + ((x & 0xF) < 10 ? '0' : 'a'-10);
  400050:	00900813          	li	a6,9
  400054:	03000593          	li	a1,48
  400058:	05700313          	li	t1,87
  40005c:	0180006f          	j	400074 <printhex+0x34>
  400060:	00c70733          	add	a4,a4,a2
  400064:	00e783a3          	sb	a4,7(a5)
    x >>= 4;
  400068:	40455513          	srai	a0,a0,0x4
  for (i = 0; i < 8; i++)
  40006c:	fff78793          	addi	a5,a5,-1
  400070:	01178c63          	beq	a5,a7,400088 <printhex+0x48>
    str[7-i] = (x & 0xF) + ((x & 0xF) < 10 ? '0' : 'a'-10);
  400074:	00f57713          	andi	a4,a0,15
  400078:	00058613          	mv	a2,a1
  40007c:	fee852e3          	bge	a6,a4,400060 <printhex+0x20>
  400080:	00030613          	mv	a2,t1
  400084:	fddff06f          	j	400060 <printhex+0x20>
  }
  str[8] = 0;
  400088:	00010623          	sb	zero,12(sp)

  printstr(str);
  40008c:	00410513          	addi	a0,sp,4
  400090:	f8dff0ef          	jal	ra,40001c <printstr>
}
  400094:	01c12083          	lw	ra,28(sp)
  400098:	02010113          	addi	sp,sp,32
  40009c:	00008067          	ret

004000a0 <list_getSize>:
struct Node g_nodePool[NUM_DISCS];

int list_getSize( struct List* list )
{
  return list->size;
}
  4000a0:	00052503          	lw	a0,0(a0)
  4000a4:	00008067          	ret

004000a8 <list_init>:

void list_init( struct List* list )
{
  list->size = 0;
  4000a8:	00052023          	sw	zero,0(a0)
  list->head = 0;
  4000ac:	00052223          	sw	zero,4(a0)
}
  4000b0:	00008067          	ret

004000b4 <list_push>:
void list_push( struct List* list, int val )
{
  struct Node* newNode;

  // Pop the next free node off the free list
  newNode = g_nodeFreeList.head;
  4000b4:	7fc00717          	auipc	a4,0x7fc00
  4000b8:	f9470713          	addi	a4,a4,-108 # 80000048 <g_nodeFreeList>
  4000bc:	00472783          	lw	a5,4(a4)
  g_nodeFreeList.head = g_nodeFreeList.head->next;
  4000c0:	0047a683          	lw	a3,4(a5)
  4000c4:	00d72223          	sw	a3,4(a4)

  // Push the new node onto the given list
  newNode->next = list->head;
  4000c8:	00452703          	lw	a4,4(a0)
  4000cc:	00e7a223          	sw	a4,4(a5)
  list->head = newNode;
  4000d0:	00f52223          	sw	a5,4(a0)

  // Assign the value
  list->head->val = val;
  4000d4:	00b7a023          	sw	a1,0(a5)

  // Increment size
  list->size++;
  4000d8:	00052783          	lw	a5,0(a0)
  4000dc:	00178793          	addi	a5,a5,1
  4000e0:	00f52023          	sw	a5,0(a0)

}
  4000e4:	00008067          	ret

004000e8 <list_pop>:

int list_pop( struct List* list )
{
  4000e8:	00050793          	mv	a5,a0
  struct Node* freedNode;
  int val;

  // Get the value from the->head of given list
  val = list->head->val;
  4000ec:	00452703          	lw	a4,4(a0)
  4000f0:	00072503          	lw	a0,0(a4)

  // Pop the head node off the given list
  freedNode = list->head;
  list->head = list->head->next;
  4000f4:	00472683          	lw	a3,4(a4)
  4000f8:	00d7a223          	sw	a3,4(a5)

  // Push the freed node onto the free list
  freedNode->next = g_nodeFreeList.head;
  4000fc:	7fc00697          	auipc	a3,0x7fc00
  400100:	f4c68693          	addi	a3,a3,-180 # 80000048 <g_nodeFreeList>
  400104:	0046a603          	lw	a2,4(a3)
  400108:	00c72223          	sw	a2,4(a4)
  g_nodeFreeList.head = freedNode;
  40010c:	00e6a223          	sw	a4,4(a3)

  // Decrement size
  list->size--;
  400110:	0007a703          	lw	a4,0(a5)
  400114:	fff70713          	addi	a4,a4,-1
  400118:	00e7a023          	sw	a4,0(a5)

  return val;
}
  40011c:	00008067          	ret

00400120 <list_clear>:

void list_clear( struct List* list )
{
  400120:	ff010113          	addi	sp,sp,-16
  400124:	00112623          	sw	ra,12(sp)
  400128:	00812423          	sw	s0,8(sp)
  40012c:	00050413          	mv	s0,a0
  while ( list_getSize(list) > 0 )
  400130:	00052783          	lw	a5,0(a0)
  400134:	00f05a63          	blez	a5,400148 <list_clear+0x28>
    list_pop(list);
  400138:	00040513          	mv	a0,s0
  40013c:	fadff0ef          	jal	ra,4000e8 <list_pop>
  while ( list_getSize(list) > 0 )
  400140:	00042783          	lw	a5,0(s0)
  400144:	fef04ae3          	bgtz	a5,400138 <list_clear+0x18>
}
  400148:	00c12083          	lw	ra,12(sp)
  40014c:	00812403          	lw	s0,8(sp)
  400150:	01010113          	addi	sp,sp,16
  400154:	00008067          	ret

00400158 <towers_init>:
  struct List pegB;
  struct List pegC;
};

void towers_init( struct Towers* this, int n )
{
  400158:	ff010113          	addi	sp,sp,-16
  40015c:	00112623          	sw	ra,12(sp)
  400160:	00812423          	sw	s0,8(sp)
  400164:	00912223          	sw	s1,4(sp)
  400168:	00058413          	mv	s0,a1
  int i;

  this->numDiscs = n;
  40016c:	00b52023          	sw	a1,0(a0)
  this->numMoves = 0;
  400170:	00052223          	sw	zero,4(a0)

  list_init( &(this->pegA) );
  400174:	00850493          	addi	s1,a0,8
  list->size = 0;
  400178:	00052423          	sw	zero,8(a0)
  list->head = 0;
  40017c:	00052623          	sw	zero,12(a0)
  list->size = 0;
  400180:	00052823          	sw	zero,16(a0)
  list->head = 0;
  400184:	00052a23          	sw	zero,20(a0)
  list->size = 0;
  400188:	00052c23          	sw	zero,24(a0)
  list->head = 0;
  40018c:	00052e23          	sw	zero,28(a0)
  list_init( &(this->pegB) );
  list_init( &(this->pegC) );

  for ( i = 0; i < n; i++ )
  400190:	00b05c63          	blez	a1,4001a8 <towers_init+0x50>
  {
    list_push( &(this->pegA), n-i );
  400194:	00040593          	mv	a1,s0
  400198:	00048513          	mv	a0,s1
  40019c:	f19ff0ef          	jal	ra,4000b4 <list_push>
  for ( i = 0; i < n; i++ )
  4001a0:	fff40413          	addi	s0,s0,-1
  4001a4:	fe0418e3          	bnez	s0,400194 <towers_init+0x3c>
  }
}
  4001a8:	00c12083          	lw	ra,12(sp)
  4001ac:	00812403          	lw	s0,8(sp)
  4001b0:	00412483          	lw	s1,4(sp)
  4001b4:	01010113          	addi	sp,sp,16
  4001b8:	00008067          	ret

004001bc <towers_clear>:

void towers_clear( struct Towers* this )
{
  4001bc:	ff010113          	addi	sp,sp,-16
  4001c0:	00112623          	sw	ra,12(sp)
  4001c4:	00812423          	sw	s0,8(sp)
  4001c8:	00050413          	mv	s0,a0

  list_clear( &(this->pegA) );
  4001cc:	00850513          	addi	a0,a0,8
  4001d0:	f51ff0ef          	jal	ra,400120 <list_clear>
  list_clear( &(this->pegB) );
  4001d4:	01040513          	addi	a0,s0,16
  4001d8:	f49ff0ef          	jal	ra,400120 <list_clear>
  list_clear( &(this->pegC) );
  4001dc:	01840513          	addi	a0,s0,24
  4001e0:	f41ff0ef          	jal	ra,400120 <list_clear>

  towers_init( this, this->numDiscs );
  4001e4:	00042583          	lw	a1,0(s0)
  4001e8:	00040513          	mv	a0,s0
  4001ec:	f6dff0ef          	jal	ra,400158 <towers_init>

}
  4001f0:	00c12083          	lw	ra,12(sp)
  4001f4:	00812403          	lw	s0,8(sp)
  4001f8:	01010113          	addi	sp,sp,16
  4001fc:	00008067          	ret

00400200 <towers_solve_h>:

void towers_solve_h( struct Towers* this, int n,
                     struct List* startPeg,
                     struct List* tempPeg,
                     struct List* destPeg )
{
  400200:	fe010113          	addi	sp,sp,-32
  400204:	00112e23          	sw	ra,28(sp)
  400208:	00812c23          	sw	s0,24(sp)
  40020c:	00912a23          	sw	s1,20(sp)
  400210:	01212823          	sw	s2,16(sp)
  400214:	01312623          	sw	s3,12(sp)
  400218:	01412423          	sw	s4,8(sp)
  40021c:	00050493          	mv	s1,a0
  400220:	00060913          	mv	s2,a2
  400224:	00070993          	mv	s3,a4
  int val;

  if ( n == 1 ) {
  400228:	00100793          	li	a5,1
  40022c:	06f58663          	beq	a1,a5,400298 <towers_solve_h+0x98>
  400230:	00068a13          	mv	s4,a3
    val = list_pop(startPeg);
    list_push(destPeg,val);
    this->numMoves++;
  }
  else {
    towers_solve_h( this, n-1, startPeg, destPeg,  tempPeg );
  400234:	fff58413          	addi	s0,a1,-1
  400238:	00068713          	mv	a4,a3
  40023c:	00098693          	mv	a3,s3
  400240:	00040593          	mv	a1,s0
  400244:	fbdff0ef          	jal	ra,400200 <towers_solve_h>
    towers_solve_h( this, 1,   startPeg, tempPeg,  destPeg );
  400248:	00098713          	mv	a4,s3
  40024c:	000a0693          	mv	a3,s4
  400250:	00090613          	mv	a2,s2
  400254:	00100593          	li	a1,1
  400258:	00048513          	mv	a0,s1
  40025c:	fa5ff0ef          	jal	ra,400200 <towers_solve_h>
    towers_solve_h( this, n-1, tempPeg,  startPeg, destPeg );
  400260:	00098713          	mv	a4,s3
  400264:	00090693          	mv	a3,s2
  400268:	000a0613          	mv	a2,s4
  40026c:	00040593          	mv	a1,s0
  400270:	00048513          	mv	a0,s1
  400274:	f8dff0ef          	jal	ra,400200 <towers_solve_h>
  }

}
  400278:	01c12083          	lw	ra,28(sp)
  40027c:	01812403          	lw	s0,24(sp)
  400280:	01412483          	lw	s1,20(sp)
  400284:	01012903          	lw	s2,16(sp)
  400288:	00c12983          	lw	s3,12(sp)
  40028c:	00812a03          	lw	s4,8(sp)
  400290:	02010113          	addi	sp,sp,32
  400294:	00008067          	ret
    val = list_pop(startPeg);
  400298:	00060513          	mv	a0,a2
  40029c:	e4dff0ef          	jal	ra,4000e8 <list_pop>
  4002a0:	00050593          	mv	a1,a0
    list_push(destPeg,val);
  4002a4:	00098513          	mv	a0,s3
  4002a8:	e0dff0ef          	jal	ra,4000b4 <list_push>
    this->numMoves++;
  4002ac:	0044a783          	lw	a5,4(s1)
  4002b0:	00178793          	addi	a5,a5,1
  4002b4:	00f4a223          	sw	a5,4(s1)
  4002b8:	fc1ff06f          	j	400278 <towers_solve_h+0x78>

004002bc <towers_solve>:

void towers_solve( struct Towers* this )
{
  4002bc:	ff010113          	addi	sp,sp,-16
  4002c0:	00112623          	sw	ra,12(sp)
  towers_solve_h( this, this->numDiscs, &(this->pegA), &(this->pegB), &(this->pegC) );
  4002c4:	01850713          	addi	a4,a0,24
  4002c8:	01050693          	addi	a3,a0,16
  4002cc:	00850613          	addi	a2,a0,8
  4002d0:	00052583          	lw	a1,0(a0)
  4002d4:	f2dff0ef          	jal	ra,400200 <towers_solve_h>
}
  4002d8:	00c12083          	lw	ra,12(sp)
  4002dc:	01010113          	addi	sp,sp,16
  4002e0:	00008067          	ret

004002e4 <towers_verify>:

int towers_verify( struct Towers* this )
{
  4002e4:	00050613          	mv	a2,a0
  struct Node* ptr;
  int numDiscs = 0;

  if ( list_getSize(&this->pegA) != 0 ) {
  4002e8:	00852783          	lw	a5,8(a0)
  4002ec:	04079a63          	bnez	a5,400340 <towers_verify+0x5c>
  return list->size;
  4002f0:	01052503          	lw	a0,16(a0)
    return 2;
  }

  if ( list_getSize(&this->pegB) != 0 ) {
  4002f4:	04051a63          	bnez	a0,400348 <towers_verify+0x64>
    return 3;
  }

  if ( list_getSize(&this->pegC) != this->numDiscs ) {
  4002f8:	00062583          	lw	a1,0(a2)
  4002fc:	01862783          	lw	a5,24(a2)
  400300:	04f59863          	bne	a1,a5,400350 <towers_verify+0x6c>
    return 4;
  }

  for ( ptr = this->pegC.head; ptr != 0; ptr = ptr->next ) {
  400304:	01c62783          	lw	a5,28(a2)
  400308:	00078e63          	beqz	a5,400324 <towers_verify+0x40>
  int numDiscs = 0;
  40030c:	00050713          	mv	a4,a0
    numDiscs++;
  400310:	00170713          	addi	a4,a4,1
    if ( ptr->val != numDiscs ) {
  400314:	0007a683          	lw	a3,0(a5)
  400318:	04e69063          	bne	a3,a4,400358 <towers_verify+0x74>
  for ( ptr = this->pegC.head; ptr != 0; ptr = ptr->next ) {
  40031c:	0047a783          	lw	a5,4(a5)
  400320:	fe0798e3          	bnez	a5,400310 <towers_verify+0x2c>
      return 5;
    }
  }

  if ( this->numMoves != ((1 << this->numDiscs) - 1) ) {
  400324:	00100793          	li	a5,1
  400328:	00b795b3          	sll	a1,a5,a1
  40032c:	fff58593          	addi	a1,a1,-1
  400330:	00462783          	lw	a5,4(a2)
  400334:	02b78463          	beq	a5,a1,40035c <towers_verify+0x78>
    return 6;
  400338:	00600513          	li	a0,6
  40033c:	00008067          	ret
    return 2;
  400340:	00200513          	li	a0,2
  400344:	00008067          	ret
    return 3;
  400348:	00300513          	li	a0,3
  40034c:	00008067          	ret
    return 4;
  400350:	00400513          	li	a0,4
  400354:	00008067          	ret
      return 5;
  400358:	00500513          	li	a0,5
  }

  return 0;
}
  40035c:	00008067          	ret

00400360 <main>:

//--------------------------------------------------------------------------
// Main

int main( int argc, char* argv[] )
{
  400360:	fd010113          	addi	sp,sp,-48
  400364:	02112623          	sw	ra,44(sp)
  400368:	02812423          	sw	s0,40(sp)
  40036c:	02912223          	sw	s1,36(sp)
  struct Towers towers;
  int i;
  int res;

  printstr("Towers of Hanoi\n");
  400370:	00000517          	auipc	a0,0x0
  400374:	10450513          	addi	a0,a0,260 # 400474 <main+0x114>
  400378:	ca5ff0ef          	jal	ra,40001c <printstr>

  printstr("NUM_DISCS = "); printhex(NUM_DISCS); printstr("\n"); 
  40037c:	00000517          	auipc	a0,0x0
  400380:	10c50513          	addi	a0,a0,268 # 400488 <main+0x128>
  400384:	c99ff0ef          	jal	ra,40001c <printstr>
  400388:	00900513          	li	a0,9
  40038c:	cb5ff0ef          	jal	ra,400040 <printhex>
  400390:	00000517          	auipc	a0,0x0
  400394:	10850513          	addi	a0,a0,264 # 400498 <main+0x138>
  400398:	c85ff0ef          	jal	ra,40001c <printstr>
  
  // Initialize free list

  list_init( &g_nodeFreeList );

  g_nodeFreeList.head = &(g_nodePool[0]);
  40039c:	7fc00717          	auipc	a4,0x7fc00
  4003a0:	cac70713          	addi	a4,a4,-852 # 80000048 <g_nodeFreeList>
  4003a4:	7fc00797          	auipc	a5,0x7fc00
  4003a8:	c5c78793          	addi	a5,a5,-932 # 80000000 <g_nodePool>
  4003ac:	00f72223          	sw	a5,4(a4)
  g_nodeFreeList.size = NUM_DISCS;
  4003b0:	00900693          	li	a3,9
  4003b4:	00d72023          	sw	a3,0(a4)
  g_nodePool[NUM_DISCS-1].next = 0;
  4003b8:	0407a223          	sw	zero,68(a5)
  g_nodePool[NUM_DISCS-1].val = 99;
  4003bc:	06300713          	li	a4,99
  4003c0:	04e7a023          	sw	a4,64(a5)
  for ( i = 0; i < (NUM_DISCS-1); i++ ) {
  4003c4:	7fc00797          	auipc	a5,0x7fc00
  4003c8:	c4478793          	addi	a5,a5,-956 # 80000008 <g_nodePool+0x8>
  4003cc:	00000713          	li	a4,0
  4003d0:	00800613          	li	a2,8
    g_nodePool[i].next = &(g_nodePool[i+1]);
  4003d4:	00070693          	mv	a3,a4
  4003d8:	00170713          	addi	a4,a4,1
  4003dc:	fef7ae23          	sw	a5,-4(a5)
    g_nodePool[i].val = i;
  4003e0:	fed7ac23          	sw	a3,-8(a5)
  for ( i = 0; i < (NUM_DISCS-1); i++ ) {
  4003e4:	00878793          	addi	a5,a5,8
  4003e8:	fec716e3          	bne	a4,a2,4003d4 <main+0x74>
  }

  towers_init( &towers, NUM_DISCS );
  4003ec:	00900593          	li	a1,9
  4003f0:	00010513          	mv	a0,sp
  4003f4:	d65ff0ef          	jal	ra,400158 <towers_init>
  towers_solve( &towers );
#endif

  // Solve it

  towers_clear( &towers );
  4003f8:	00010513          	mv	a0,sp
  4003fc:	dc1ff0ef          	jal	ra,4001bc <towers_clear>
  //setStats(1);
  towers_solve( &towers );
  400400:	00010513          	mv	a0,sp
  400404:	eb9ff0ef          	jal	ra,4002bc <towers_solve>
  //setStats(0);
  
  // Chek the results
  res = towers_verify( &towers );
  400408:	00010513          	mv	a0,sp
  40040c:	ed9ff0ef          	jal	ra,4002e4 <towers_verify>
  400410:	00050413          	mv	s0,a0
  printstr("Result = "); printhex(res); printstr("\n");
  400414:	00000517          	auipc	a0,0x0
  400418:	08850513          	addi	a0,a0,136 # 40049c <main+0x13c>
  40041c:	c01ff0ef          	jal	ra,40001c <printstr>
  400420:	00040513          	mv	a0,s0
  400424:	c1dff0ef          	jal	ra,400040 <printhex>
  400428:	00000517          	auipc	a0,0x0
  40042c:	07050513          	addi	a0,a0,112 # 400498 <main+0x138>
  400430:	bedff0ef          	jal	ra,40001c <printstr>
 
  unsigned int count = COUNTER;
  400434:	ffff07b7          	lui	a5,0xffff0
  400438:	0207a483          	lw	s1,32(a5) # ffff0020 <__stack_init+0x7bff0024>
  printstr("Total Clock Ticks = "); printhex(count); printstr("\n"); 
  40043c:	00000517          	auipc	a0,0x0
  400440:	06c50513          	addi	a0,a0,108 # 4004a8 <main+0x148>
  400444:	bd9ff0ef          	jal	ra,40001c <printstr>
  400448:	00048513          	mv	a0,s1
  40044c:	bf5ff0ef          	jal	ra,400040 <printhex>
  400450:	00000517          	auipc	a0,0x0
  400454:	04850513          	addi	a0,a0,72 # 400498 <main+0x138>
  400458:	bc5ff0ef          	jal	ra,40001c <printstr>

  return res;
}
  40045c:	00040513          	mv	a0,s0
  400460:	02c12083          	lw	ra,44(sp)
  400464:	02812403          	lw	s0,40(sp)
  400468:	02412483          	lw	s1,36(sp)
  40046c:	03010113          	addi	sp,sp,48
  400470:	00008067          	ret
