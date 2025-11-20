#ifndef PLATFORM_LOGISIM_C
#define PLATFORM_LOGISIM_C

#include "platform.h"
#include "hardware.h"
#include "file_system.h"
#include "utils.h"


struct console_config {
    const char *name;
    volatile char *display_addr;
    volatile char *keyboard_activated_addr;
    volatile char *keyboard_symbol_addr;
};

static struct console_config consoles[] = {
    {"/console0", (volatile char*)0xFFF00004, (volatile char*)0xFFF00200, (volatile char*)0xFFF00100},
    {"/console1", (volatile char*)0xFFF00008, (volatile char*)0xFFF00204, (volatile char*)0xFFF00104},
};
#define NUM_CONSOLES (sizeof(consoles)/sizeof(consoles[0]))

int platform_console_fd[NUM_CONSOLES];

static void init_console_fds(void) {
    for (int i = 0; i < NUM_CONSOLES; i++) {
        platform_console_fd[i] = -1;
    }
}

int platform_console_write(int minor, const void *buf, int n, int off) {
    (void)off;
    if (minor < 0 || minor >= NUM_CONSOLES) return -1;

    const char *p = buf;
    int written = 0;
    for (int i = 0; i < n; i++) {
        *consoles[minor].display_addr = p[i];
        platform_print_char(p[i]);
        written++;
    }
    return written;
}

void platform_init(void) {
    init_console_fds();
}


int platform_create_consoles(void) {
    platform_print_string("Initializing console devices...\n");

    int success_count = 0;

    for (int i = 0; i < NUM_CONSOLES; i++) {
        const char *console_name = consoles[i].name;

        int inum = fs_mknod(console_name, DEV_CONSOLE, i);
        if (inum < 0) {
            platform_print_string("ERROR: Failed to create console device ");
            platform_print_num(i);
            platform_print_string("\n");
            continue;
        }

        platform_console_fd[i] = fs_open(console_name, O_RDWR);
        if (platform_console_fd[i] < 0) {
            platform_print_string("ERROR: Failed to open console ");
            platform_print_num(i);
            platform_print_string("\n");
            continue;
        }

        platform_print_string("Console ");
        platform_print_num(i);
        platform_print_string(" ready as fd ");
        platform_print_num(platform_console_fd[i]);
        platform_print_string("\n");

        success_count++;
    }

    platform_print_string("Initialized ");
    platform_print_num(success_count);
    platform_print_string("/");
    platform_print_num(NUM_CONSOLES);
    platform_print_string(" consoles\n");

    return (success_count > 0) ? 0 : -1;
}

int platform_open_consoles(void) {
    int success_count = 0;

    for (int i = 0; i < NUM_CONSOLES; i++) {
        platform_console_fd[i] = fs_open(consoles[i].name, O_RDWR);
        if (platform_console_fd[i] < 0) {
            platform_print_string("Failed to open console ");
            platform_print_num(i);
            platform_print_string("\n");
            continue;
        }
        success_count++;
    }

    return (success_count == NUM_CONSOLES) ? 0 : -1;
}


int platform_get_console_fd(int console_num) {
    if (console_num < 0 || console_num >= NUM_CONSOLES) return -1;
    return platform_console_fd[console_num];
}

int platform_write_console(int console_num, const char *str) {
    int fd = platform_get_console_fd(console_num);
    if (fd < 0) return -1;
    platform_print_string(str);
    platform_print_num(fd);
    platform_print_num(strlen(str));
    return write(fd, str, strlen(str));
}

int platform_write_console_num(int console_num, int num) {
    int fd = platform_get_console_fd(console_num);
    if (fd < 0) return -1;

    char buffer[16];
    char *p = buffer + sizeof(buffer) - 1;
    *p = '\0';

    int is_negative = 0;
    if (num < 0) {
        is_negative = 1;
        num = -num;
    }

    do {
        *--p = '0' + (num % 10);
        num /= 10;
    } while (num > 0);

    if (is_negative) {
        *--p = '-';
    }

    return write(fd, p, strlen(p));
}

int platform_console_read(int minor, void *buf, int n, int off) {
    (void)off;
    if (minor < 0 || minor >= NUM_CONSOLES) return -1;
    if (n <= 0) return 0;

    volatile char *available = consoles[minor].keyboard_activated_addr;
    volatile char *keyreg = consoles[minor].keyboard_symbol_addr;

    int count = 0;
    char *out = (char *)buf;

    while (count < n) {
        if (*available != 0) {
            char key = *keyreg;
            *available = 0;
            out[count++] = key;

            *consoles[minor].display_addr = key;
        } else {
            if (count > 0)
                break;
        }
    }

    return count;
}

int platform_write_all_consoles(const char *str) {
    int success_count = 0;
    for (int i = 0; i < NUM_CONSOLES; i++) {
        if (platform_write_console(i, str) > 0) {
            success_count++;
        }
    }
    return success_count;
}

int platform_get_console_count(void) {
    return NUM_CONSOLES;
}

int platform_console_available(int console_num) {
    return (console_num >= 0 && console_num < NUM_CONSOLES &&
            platform_console_fd[console_num] >= 0);
}

int platform_read_block(int block, void *buf) {
    if (block < 0) return -1;
    *hd_mem_addr = (int)buf;
    *hd_sector = block;
    *hd_command = 1;
    hd_wait();
    return (*hd_status & 0x2) ? -1 : 0;
}

int platform_write_block(int block, const void *buf) {
    if (block < 0) return -1;
    *hd_mem_addr = (int)buf;
    *hd_sector = block;
    *hd_command = 2;
    hd_wait();
    return (*hd_status & 0x2) ? -1 : 0;
}

void platform_print_char(char c) {
    *OUT_ADDR = c;
}

void platform_print_string(const char *s) {
    while (*s) *OUT_ADDR = *s++;
}

void platform_print_num(int n) {
    if (n < 0) {
        platform_print_char('-');
        n = -n;
    }
    if (n >= 10) platform_print_num(n / 10);
    platform_print_char('0' + (n % 10));
}

#endif