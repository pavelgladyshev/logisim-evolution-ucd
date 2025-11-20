#include "platform.h"
#include "file_system.h"
#include <windows.h>
#include <stdio.h>
#include <conio.h>
//#include <string.h>

static FILE* hd_file = NULL;

void platform_init(void) {
    hd_file = fopen("hd.img", "r+b");
    if (!hd_file) {
        hd_file = fopen("hd.img", "w+b");
    }
}

int platform_read_block(int block, void *buf) {
    if (block < 0 || !hd_file) return -1;
    if (fseek(hd_file, block * BLOCK_SIZE, SEEK_SET) != 0) return -1;
    return fread(buf, BLOCK_SIZE, 1, hd_file) == 1 ? 0 : -1;
}

int platform_write_block(int block, const void *buf) {
    if (block < 0 || !hd_file) return -1;
    if (fseek(hd_file, block * BLOCK_SIZE, SEEK_SET) != 0) return -1;
    if (fwrite(buf, BLOCK_SIZE, 1, hd_file) != 1) return -1;
    fflush(hd_file);
    return 0;
}

void platform_print_char(char c) {
    putchar(c);
}

void platform_print_string(const char *s) {
    fputs(s, stdout);
    fflush(stdout);
}

void platform_print_num(int n) {
    printf("%d", n);
}

int platform_console_read(int minor, void *buf, int n, int off) {
    (void)minor; (void)off;
    if (n <= 0) return 0;
    if (_kbhit()) {
        ((char*)buf)[0] = _getch();
        return 1;
    }
    return 0;
}

int platform_console_write(int minor, const void *buf, int n, int off) {
    (void)minor; (void)off;
    fwrite(buf, 1, n, stdout);
    fflush(stdout);
    return n;
}