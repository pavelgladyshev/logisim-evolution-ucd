# Simple test program to verify functionality of added lui and auipc instructions for Logism rv32im simulated CPU.
# To load into simulated memory (ROM) : Assemble, Dump Memory (.text) as Hexadecimal Text, load image into ROM as v2.0 raw

.text
auipc x24, 0x23056   # set x24 to pc + 0x23056000 
addi x24,x24, 0x789  # set lower 12 bits of x24 to 0x789
lui x23, 0x12345     # set upper 20 bits of x23 to 0x12345
addi x23,x23, 0x678  # set lower 12 bits of x23 to 0x678
halt:
j halt