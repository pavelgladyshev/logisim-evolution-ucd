li t1, 0xaaaa
li t2, 0x10010000
sh t1, 0(t2)
addi t2, t2, 1
#sh t1, 0(t2) # HALTS!
addi t2, t2, 1
sh t1, 0(t2)
addi t2, t2, 1
#sh t1, 0(t2) # HALTS!
addi t2, t2, 1
sh t1, 0(t2)