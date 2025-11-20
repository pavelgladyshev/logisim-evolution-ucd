#include "syscall.h"
#include "utils.h"
static inline int do_syscall(int num, int arg0, int arg1, int arg2, int arg3) {
    register int a0 asm("a0") = arg0;
    register int a1 asm("a1") = arg1;
    register int a2 asm("a2") = arg2;
    register int a3 asm("a3") = arg3;
    register int a7 asm("a7") = num;

    asm volatile("ecall"
                 : "+r"(a0)
                 : "r"(a1), "r"(a2), "r"(a3), "r"(a7)
                 : "memory");

    return a0;
}



int sys_open(const char *path, int flags) {
    return do_syscall(SYS_OPEN, (int)path, flags, 0, 0);
}

int sys_close(int fd) {
    return do_syscall(SYS_CLOSE, fd, 0, 0, 0);
}

int sys_read(int fd, void *buf, int n) {
    return do_syscall(SYS_READ, fd, (int)buf, n, 0);
}

int sys_write(int fd, const void *buf, int n) {
    return do_syscall(SYS_WRITE, fd, (int)buf, n, 0);
}

int sys_write_str(int fd, const char *str) {
    return sys_write(fd, str, strlen(str));
}

int sys_mkdir(const char *path) {
    return do_syscall(SYS_MKDIR, (int)path, 0, 0, 0);
}

int sys_rmdir(const char *path) {
    return do_syscall(SYS_RMDIR, (int)path, 0, 0, 0);
}

int sys_readdir(int fd, struct dirent *entries, int count) {
    return do_syscall(SYS_READDIR, fd, (int)entries, count, 0);
}

int sys_unlink(const char *path) {
    return do_syscall(SYS_UNLINK, (int)path, 0, 0, 0);
}

int sys_chdir(const char *path) {
    return do_syscall(SYS_CHDIR, (int)path, 0, 0, 0);
}

int sys_getcwd(char *buf, int size) {
    return do_syscall(SYS_GETCWD, (int)buf, size, 0, 0);
}

void sys_exit(void) {
    do_syscall(SYS_EXIT, 0, 0, 0, 0);
    while (1);
}
