.text 
li t1, 0xef12
li t2, 0x10010000
sh t1, 0(t2)
addi t2, t2, 2
li t1, 0xabcd
sh t1, 0(t2)