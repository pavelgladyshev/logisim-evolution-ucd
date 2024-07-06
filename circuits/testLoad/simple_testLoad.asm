# Test for Load Instructions 
# To run in Logisim, assemble file, export .data as Hexadecimal text (load into RAM) and .text as Hexadecimal text (load into ROM)

.data
number: .word 0x12345678
negative_word: .word -30 
negative_half: .half -30
negative_byte: .byte -30
.text

# test lw
la t0,number
lw s2,0(t0)
la t0,negative_word 
lw s2,0(t0)

# test lh 
la t0,negative_half
lh s3, 0(t0) 
la t0,number
lh s3, 2(t0)
lh s3, 0(t0)

# test lhu 
la t0,number
lhu s4, 2(t0)
lhu s4, 0(t0)

# test lb 
la t0,negative_byte
lb s5, 0(t0)

# test lbu 
la t0,number
lbu s6,3(t0)
lbu s6,2(t0)
lbu s6,1(t0)
lbu s6,0(t0)
