#include "trap.h"
#include "syscall.h"
#include "process.h"
#include "console.h"
#include "file_system.h"

extern int current_pid;



void c_trap_handler(trap_frame_t *tf) {
    int cause = get_mcause();



    switch (cause) {
        case MCAUSE_ECALL_FROM_M_MODE:
            tf->a0 = handle_syscall(tf->a7, tf->a0, tf->a1, tf->a2, tf->a3);
            tf->mepc += 4;
            break;

        default:
            print_string("Panic: unexpected exception or interrupt!\n");
            for(;;);
    }
}