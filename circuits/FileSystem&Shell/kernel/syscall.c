#include "syscall.h"
#include "process.h"
#include "trap.h"
#include "file_system.h"

int sys_open(const char *path, int flags) {
    if (!path) return -1;
    return fs_open(path, flags);
}

int sys_close(int fd) {
    return close(fd);
}

int sys_read(int fd, void *buf, int n) {
    if (!buf || n < 0) return -1;
    return read(fd, buf, n);
}

int sys_write(int fd, const void *buf, int n) {
    if (!buf || n < 0) return -1;
    return write(fd, buf, n);
}

int sys_write_str(int fd, const char *str) {
    if (!str) return -1;
    int len = strlen(str);
    return sys_write(fd, str, len);
}

int sys_mkdir(const char *path) {
    if (!path) return -1;
    return fs_mkdir(path);
}

int sys_rmdir(const char *path) {
    if (!path) return -1;
    return fs_rmdir(path);
}

int sys_unlink(const char *path) {
    if (!path) return -1;
    return fs_unlink(path);
}

int sys_chdir(const char *path) {
    if (!path) return -1;
    return fs_chdir(path);
}

int sys_getcwd(char *buf, int size) {
    if (!buf || size <= 0) return -1;
    return fs_getcwd(buf, size);
}

int sys_readdir(int fd, struct dirent *entries, int count) {
    if (fd < 0 || fd >= MAXFILES || fileTable[fd].inum == -1)
        return -1;
    return fs_readdir(fileTable[fd].inum, entries, count);
}


int handle_syscall(int syscall_num, int arg0, int arg1, int arg2, int arg3) {
    (void)arg3;
    switch (syscall_num) {
        case SYS_OPEN:     return sys_open((const char*)arg0, arg1);
        case SYS_CLOSE:    return sys_close(arg0);
        case SYS_READ:     return sys_read(arg0, (void*)arg1, arg2);
        case SYS_WRITE:    return sys_write(arg0, (const void*)arg1, arg2);
        case SYS_MKDIR:    return sys_mkdir((const char*)arg0);
        case SYS_RMDIR:    return sys_rmdir((const char*)arg0);
        case SYS_UNLINK:   return sys_unlink((const char*)arg0);
        case SYS_CHDIR:    return sys_chdir((const char*)arg0);
        case SYS_GETCWD:   return sys_getcwd((char*)arg0, arg1);
        case SYS_READDIR:  return sys_readdir(arg0, (struct dirent*)arg1, arg2);
        case SYS_EXIT:     return 0;
        default:           return -1;
    }
}
