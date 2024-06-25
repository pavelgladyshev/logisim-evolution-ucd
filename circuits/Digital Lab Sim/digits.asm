.data

.text
main:
    # Display digits 0 to 9 on the left display
    li t1, 0xffff0011
    li t0, 0x3F  # 0
    sb t0, 0(t1)
    li t0, 0x06  # 1
    sb t0, 0(t1)
    li t0, 0x5B  # 2
    sb t0, 0(t1)
    li t0, 0x4F  # 3
    sb t0, 0(t1)
    li t0, 0x66  # 4
    sb t0, 0(t1)
    li t0, 0x6D  # 5
    sb t0, 0(t1)
    li t0, 0x7D  # 6
    sb t0, 0(t1)
    li t0, 0x07  # 7
    sb t0, 0(t1)
    li t0, 0x7F  # 8
    sb t0, 0(t1)
    li t0, 0x6F  # 9
    sb t0, 0(t1)

    # Display digits 0 to 9 on the right display
    li t1, 0xffff0010
    li t0, 0x3F  # 0
    sb t0, 0(t1)
    li t0, 0x06  # 1
    sb t0, 0(t1)
    li t0, 0x5B  # 2
    sb t0, 0(t1)
    li t0, 0x4F  # 3
    sb t0, 0(t1)
    li t0, 0x66  # 4
    sb t0, 0(t1)
    li t0, 0x6D  # 5
    sb t0, 0(t1)
    li t0, 0x7D  # 6
    sb t0, 0(t1)
    li t0, 0x07  # 7
    sb t0, 0(t1)
    li t0, 0x7F  # 8
    sb t0, 0(t1)
    li t0, 0x6F  # 9
    sb t0, 0(t1)

    # Exit program
    li a7, 10  # Syscall for exit
    ecall
