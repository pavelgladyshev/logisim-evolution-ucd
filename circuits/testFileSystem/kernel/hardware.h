#ifndef HW_H
#define HW_H

#define HD_BASE 0x00110000
static volatile int* hd_command = (volatile int*)HD_BASE;
static volatile int* hd_mem_addr = (volatile int*)(HD_BASE + 4);
static volatile int* hd_sector = (volatile int*)(HD_BASE + 8);
static volatile int* hd_status = (volatile int*)(HD_BASE + 0xC);

#define OUT_ADDR ((volatile char*)0xFFF00000)

inline static void hd_wait() {
    while (*hd_status & 0x1);
}

static inline void dbg(char c) { *OUT_ADDR = c; }

#endif