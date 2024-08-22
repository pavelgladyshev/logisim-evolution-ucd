
	.text
	
main:
	# set-up trap handler
 	la s0,trap_handler
 	csrw s0,mtvec       # set mtvec = address of the handler
 	
 	# enable processing of interrup requests from timer
 	li s1,0x80        
 	csrw s1,mie         # set mie = 0x80 

 	# enable interrupts in CPU
 	csrsi mstatus,8    # set interrupt enable bit in mstatus (0) 
 	
 	li s0, 0x0c000004 # sets priority of first keyboard
 	li s1, 2
 	sw s1, 0(s0)
 	
 	li s0, 0x0c000008 # sets priority of second keyboard
 	li s1, 1
 	sw s1, 0(s0)
 	
 	li s0, 0x0c002000 # enable keyboard 1 interrupts
 	li s1, 1
 	sw s1, 0(s0)
 	
 	li s1, 2	  # enable keyboard 2 interrupts
 	sw s1, 0(s0)
 
 	wfi
 	
trap_handler: 
	li s0, 0x0c000004
 	lw s1, 0(s0)
 	
 	li s0, 0x0c000008
 	lw s2, 0(s0)
 	
	li s0, 0x0c001000
 	lw s3, 0(s0)
 	
 	andi s3, s3, 6 
 	
 	li s4, 2
 	li s5, 4
 	li s6, 6
 	
 	beq s3, s4, print1
 	beq s3, s5, print2
 	beq s3, s6, consider

consider:
	blt s1, s2, print1
	j print2 	 	
	
print1:
	la s1, print_keyboard1
	csrw s1,mepc
	
	li s0, 0x0c200004
	li s1, 1
	sw s1, 0(s0)
	j exit
	
print2:
	la s1, print_keyboard2
	csrw s1, mepc
	
	li s0, 0x0c200004
	li s1, 2
	sw s1, 0(s0)
	j exit
	
exit:                
	mret                # restore pc from mepc and continue running
	                    # selected process 	
 		

print_keyboard1:
	li s0, 0xffff0004
	lw s1, 0(s0)
	li s2, 0xffff000c
	sw s1, 0(s2)
	
	li s0, 0x0c200004
	li s1, 0
	sw s1, 0(s0)
	wfi
	
print_keyboard2:
	li s0, 0xffff0104
	lw s1, 0(s0)
	li s2, 0xffff000c
	sw s1, 0(s2)
	
	li s0, 0x0c200004
	li s1, 0
	sw s1, 0(s0)
	wfi	
