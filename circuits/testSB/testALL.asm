.text 
li t1, 0x12
li t2, 0x10010000
sb t1, 0(t2)
li t1, 0xef
addi t2, t2, 1
sb t1, 0(t2)
li t1, 0xcd
addi t2, t2, 1
sb t1, 0(t2)
li t1, 0xab
addi t2, t2, 1
sb t1, 0(t2)