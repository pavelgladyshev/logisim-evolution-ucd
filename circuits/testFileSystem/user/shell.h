#ifndef SHELL_H
#define SHELL_H

void shell(int fd);
struct command_desc {
    const char *name;
    int min_args;
    const char *usage;
    int (*handler)(int fd, int argc, char **argv);
};
#endif