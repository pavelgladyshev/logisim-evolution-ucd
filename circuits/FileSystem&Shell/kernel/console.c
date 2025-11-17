#include "console.h"
#include "file_system.h"
#include "platform.h"

#ifdef WINDOWS
#include <stdio.h>
#include <conio.h>
#endif

static int console_write(int minor, const void *buf, int n, int off) {
    (void)off;
#ifdef WINDOWS
    (void)minor;
    fwrite(buf, 1, n, stdout);
    fflush(stdout);
    return n;
#else
    return platform_console_write(minor, buf, n, off);
#endif
}

static int console_read(int minor, void *buf, int n, int off) {
    (void)off;
#ifdef WINDOWS
    (void)minor;
    if (n <= 0) return 0;
    if (_kbhit()) {
        char key = _getch();
        ((char*)buf)[0] = key;
        return 1;
    }
    return 0;
#else
    return platform_console_read(minor, buf, n, off);
#endif
}

void console_init(void) {
    struct dev_ops ops = {
        .read = console_read,
        .write = console_write
    };
    dev_register(DEV_CONSOLE, &ops);
}
