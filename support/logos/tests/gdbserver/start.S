# The gdb server's test program: start-up, a trap handler for ecall, and the ecall itself
    .section .text.start
    .globl _start
_start:
    li   sp, 0x110000          # the top of the RAM
    la   t0, trap_handler
    csrw mtvec, t0
    li   t0, 0x5ca7c4ed
    csrw mscratch, t0          # a value for gdb to find there
    li   t0, 0x808             # mie: MSIE and MEIP's enable bit (mstatus.MIE stays 0: no interrupts)
    csrw mie, t0
    call main
1:  j    1b

# do_ecall: an environment call; the handler goes on after it
    .text
    .globl do_ecall
do_ecall:
    ecall
    ret

# trap_handler: mepc is the ecall's address, mcause 11 (an ecall from M-mode); go on after the ecall. It may
# change t0: ecall is in a function, which the calling convention lets change t0
    .globl trap_handler
    .balign 4
trap_handler:
    csrr t0, mepc
    addi t0, t0, 4
    csrw mepc, t0
    mret
