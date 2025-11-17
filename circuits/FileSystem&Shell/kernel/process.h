#pragma once

#include "trap.h"

enum process_state
{
    UNUSED,
    RUNNING
};

typedef struct process {
    int pid;
    int state;
    trap_frame_t tf;
} process_t;