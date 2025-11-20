#ifndef SYSCALL_H
#define SYSCALL_H


#define SYS_OPEN     1
#define SYS_READ     2
#define SYS_WRITE    3
#define SYS_CLOSE    4
#define SYS_MKDIR    5
#define SYS_RMDIR    6
#define SYS_READDIR  7
#define SYS_EXIT     8
#define SYS_SEEK     9
#define SYS_UNLINK   10
#define SYS_CHDIR    11
#define SYS_GETCWD   12

#define MAX_NAME_LEN 28


#ifdef __USER_SPACE__
    int sys_open(const char *path, int flags);
    int sys_close(int fd);
    int sys_read(int fd, void *buf, int n);
    int sys_write(int fd, const void *buf, int n);
    int sys_write_str(int fd, const char *str);
    int sys_mkdir(const char *path);
    int sys_rmdir(const char *path);
    int sys_unlink(const char *path);
    int sys_chdir(const char *path);
    int sys_getcwd(char *buf, int size);
    int sys_readdir(int fd, struct dirent *entries, int count);
    void sys_exit(void);

#else
    int handle_syscall(int syscall_num, int arg0, int arg1, int arg2, int arg3);
#endif

#endif