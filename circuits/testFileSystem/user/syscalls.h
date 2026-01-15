
#ifndef SYSCALLS_H
#define SYSCALLS_H
#include "file_system.h"
#define SYS_OPEN     1
#define SYS_CLOSE    2
#define SYS_READ     3
#define SYS_WRITE    4
#define SYS_MKDIR    5
#define SYS_RMDIR    6
#define SYS_READDIR  7
#define SYS_UNLINK   8
#define SYS_CHDIR    9
#define SYS_GETCWD   10
#define SYS_EXIT     11



int sys_open(const char *path, int flags);
int sys_close(int fd);
int sys_read(int fd, void *buf, int n);
int sys_write(int fd, const void *buf, int n);
int sys_write_str(int fd, const char *str);
int sys_mkdir(const char *path);
int sys_rmdir(const char *path);
int sys_readdir(int fd, struct dirent *entries, int count);
int sys_unlink(const char *path);
int sys_chdir(const char *path);
int sys_getcwd(char *buf, int size);
void sys_exit(void);

#endif
