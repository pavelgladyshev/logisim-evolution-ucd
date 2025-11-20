#include "syscalls.h"
#include "shell.h"
#include "file_system.h"
int main(void) {
    int fd;

    fd = sys_open("/console0", O_RDWR);

    sys_write(fd, "\n==============================\n", 32);
    sys_write(fd, ">>> USER main() ENTERED <<<\n", 29);
    sys_write(fd, "==============================\n\n", 33);

    shell(fd);

    sys_write(fd, "[INFO] main() exiting normally\n", 31);
    return 0;
}
