#include "syscall.h"
#include "shell.h"
#include "file_system.h"

#define BUF_SIZE 128
#define MAX_ARGS 16

// ---------- Utility Helpers ----------

static void print_errno(int fd, const char *cmd, const char *path, const char *msg) {
    sys_write_str(fd, cmd);
    if (path) {
        sys_write_str(fd, ": ");
        sys_write_str(fd, path);
    }
    if (msg) {
        sys_write_str(fd, ": ");
        sys_write_str(fd, msg);
    }
    sys_write_str(fd, "\n");
}

static void print_prompt(int fd) {
    char cwd[128];
    if (sys_getcwd(cwd, sizeof(cwd)) < 0) {
        sys_write_str(fd, "$ ");
        return;
    }
    sys_write_str(fd, cwd);
    sys_write_str(fd, "$ ");
}

// ---------- Input & Parsing ----------

static int read_input(int fd, char *buf, int max) {
    int len = 0;
    while (len < max - 1) {
        char c;
        int n = sys_read(fd, &c, 1);
        if (n <= 0) continue;
        if (c == '\n' || c == '\r') {
            buf[len] = '\0';
            return len;
        }
        buf[len++] = c;
    }
    buf[len] = '\0';
    return len;
}

static int parse_args(char *buf, char **argv, int max_args) {
    int argc = 0;
    char *token = strtok(buf, " ");
    while (token && argc < max_args) {
        argv[argc++] = token;
        token = strtok(0, " ");
    }
    return argc;
}

// ---------- Command Handlers ----------

static int handle_ls(int fd, int argc, char **argv) {
    const char *target = (argc > 1) ? argv[1] : ".";

    int dir = sys_open(target, O_RDONLY);
    if (dir < 0) {
        print_errno(fd, "ls", target, "cannot open directory");
        return -1;
    }

    struct dirent entries[16];
    int count = sys_readdir(dir, entries, 16);
    if (count < 0) {
        print_errno(fd, "ls", target, "cannot read directory");
        sys_close(dir);
        return -1;
    }

    for (int i = 0; i < count; i++) {
        if (strcmp(entries[i].name, ".") == 0 ||  strcmp(entries[i].name, "..") == 0)
            continue;

        sys_write_str(fd, entries[i].name);
        sys_write(fd, "\n", 1);
    }

    sys_close(dir);
    return 0;
}



static int handle_mkdir(int fd, int argc, char **argv) {
    if (sys_mkdir(argv[1]) < 0) {
        print_errno(fd, "mkdir", argv[1], "cannot create directory");
        return -1;
    }
    return 0;
}

static int handle_rmdir(int fd, int argc, char **argv) {
    if (sys_rmdir(argv[1]) < 0) {
        print_errno(fd, "rmdir", argv[1], "failed to remove directory");
        return -1;
    }
    return 0;
}

static int handle_cat(int fd, int argc, char **argv) {
    int fd_file = sys_open(argv[1], O_RDONLY);
    if (fd_file < 0) {
        print_errno(fd, "cat", argv[1], "No such file or directory");
        return -1;
    }

    char data[64];
    int n;
    while ((n = sys_read(fd_file, data, sizeof(data))) > 0)
        sys_write(fd, data, n);
    sys_close(fd_file);
    return 0;
}

static int handle_echo(int fd, int argc, char **argv) {
    char *redir = 0;
    int last_arg = argc;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], ">") == 0 && i + 1 < argc) {
            redir = argv[i + 1];
            last_arg = i;
            break;
        }
    }

    if (!redir) {
        for (int i = 1; i < argc; i++) {
            sys_write(fd, argv[i], strlen(argv[i]));
            if (i < argc - 1) sys_write(fd, " ", 1);
        }
        sys_write(fd, "\n", 1);
    } else {
        int fd_file = sys_open(redir, O_WRONLY | O_CREAT | O_TRUNC);
        if (fd_file < 0) {
            print_errno(fd, "echo", redir, "cannot write to file");
            return -1;
        }
        for (int i = 1; i < last_arg; i++) {
            sys_write(fd_file, argv[i], strlen(argv[i]));
            if (i < last_arg - 1) sys_write(fd_file, " ", 1);
        }
        sys_close(fd_file);
    }
    return 0;
}

static int handle_touch(int fd, int argc, char **argv) {
    int f = sys_open(argv[1], O_WRONLY | O_CREAT);
    if (f < 0) {
        print_errno(fd, "touch", argv[1], "cannot create file");
        return -1;
    }
    sys_close(f);
    return 0;
}

static int handle_rm(int fd, int argc, char **argv) {
    if (sys_unlink(argv[1]) < 0) {
        print_errno(fd, "rm", argv[1], "No such file or directory");
        return -1;
    }
    return 0;
}

static int handle_cp(int fd, int argc, char **argv) {
    int src = sys_open(argv[1], O_RDONLY);
    if (src < 0) {
        print_errno(fd, "cp", argv[1], "cannot open source file");
        return -1;
    }
    int dst = sys_open(argv[2], O_WRONLY | O_CREAT | O_TRUNC);
    if (dst < 0) {
        print_errno(fd, "cp", argv[2], "cannot open destination file");
        sys_close(src);
        return -1;
    }

    char buf[128];
    int n;
    while ((n = sys_read(src, buf, sizeof(buf))) > 0)
        sys_write(dst, buf, n);

    sys_close(src);
    sys_close(dst);
    return 0;
}

static int handle_wc(int fd, int argc, char **argv) {
    int file = sys_open(argv[1], O_RDONLY);
    if (file < 0) {
        print_errno(fd, "wc", argv[1], "No such file or directory");
        return -1;
    }

    char buf[64];
    int n, lines = 0, words = 0, bytes = 0, in_word = 0;
    while ((n = sys_read(file, buf, sizeof(buf))) > 0) {
        bytes += n;
        for (int i = 0; i < n; i++) {
            if (buf[i] == '\n') lines++;
            if (buf[i] == ' ' || buf[i] == '\n' || buf[i] == '\t')
                in_word = 0;
            else if (!in_word) {
                words++;
                in_word = 1;
            }
        }
    }
    sys_close(file);

    // Print without snprintf()
    sys_write_str(fd, "  ");
    char num[16];
    itoa(lines, num, 10);
    sys_write_str(fd, num);
    sys_write_str(fd, "  ");
    itoa(words, num, 10);
    sys_write_str(fd, num);
    sys_write_str(fd, "  ");
    itoa(bytes, num, 10);
    sys_write_str(fd, num);
    sys_write_str(fd, " ");
    sys_write_str(fd, argv[1]);
    sys_write(fd, "\n", 1);
    return 0;
}

static int handle_clear(int fd, int argc, char **argv) {
    (void)argc; (void)argv;
    sys_write_str(fd, "\033[2J\033[H");
    return 0;
}

static int handle_pwd(int fd, int argc, char **argv) {
    (void)argc; (void)argv;
    char buf[128];
    if (sys_getcwd(buf, sizeof(buf)) < 0) {
        print_errno(fd, "pwd", "", "error retrieving current directory");
        return -1;
    }
    sys_write_str(fd, buf);
    sys_write(fd, "\n", 1);
    return 0;
}

static int handle_cd(int fd, int argc, char **argv) {
    if (sys_chdir(argv[1]) < 0) {
        print_errno(fd, "cd", argv[1], "No such file or directory");
        return -1;
    }
    return 0;
}

static int handle_help(int fd, int argc, char **argv);

static int handle_exit(int fd, int argc, char **argv) {
    (void)argc; (void)argv;
    sys_write_str(fd, "Exiting shell.\n");
    sys_exit();
    return 0;
}

// ---------- Command Table ----------

static const struct command_desc commands[] = {
    {"ls",      1, "ls                    - list directory contents", handle_ls},
    {"mkdir",   2, "mkdir <dir>           - create directory", handle_mkdir},
    {"rmdir",   2, "rmdir <dir>           - remove directory", handle_rmdir},
    {"cat",     2, "cat <file>            - display file contents", handle_cat},
    {"echo",    1, "echo [text] [> file]  - echo text to stdout or file", handle_echo},
    {"touch",   2, "touch <file>          - create empty file", handle_touch},
    {"rm",      2, "rm <file>             - remove file", handle_rm},
    {"cp",      3, "cp <src> <dst>        - copy file", handle_cp},
    {"wc",      2, "wc <file>             - count lines, words, bytes", handle_wc},
    {"clear",   1, "clear                 - clear screen", handle_clear},
    {"pwd",     1, "pwd                   - print working directory", handle_pwd},
    {"cd",      2, "cd <dir>              - change directory", handle_cd},
    {"help",    1, "help                  - show available commands", handle_help},
    {"exit",    1, "exit                  - exit shell", handle_exit},
};

#define COMMAND_COUNT (sizeof(commands)/sizeof(commands[0]))

static int handle_help(int fd, int argc, char **argv) {
    (void)argc; (void)argv;
    sys_write_str(fd, "Available commands:\n");
    for (int i = 0; i < COMMAND_COUNT; i++) {
        sys_write_str(fd, "  ");
        sys_write_str(fd, commands[i].usage);
        sys_write(fd, "\n", 1);
    }
    return 0;
}

// ---------- Main Shell Loop ----------

void shell(int fd) {
    char buf[BUF_SIZE];
    char *argv[MAX_ARGS];
    int argc;

    sys_write_str(fd, "\n");
    print_prompt(fd);
    sys_chdir("/");

    while (1) {
        int n = read_input(fd, buf, BUF_SIZE);
        if (n <= 0) continue;

        argc = parse_args(buf, argv, MAX_ARGS);
        if (argc == 0) {
            print_prompt(fd);
            continue;
        }

        int found = 0;
        for (int i = 0; i < COMMAND_COUNT; i++) {
            if (strcmp(argv[0], commands[i].name) == 0) {
                found = 1;
                if (argc < commands[i].min_args) {
                    sys_write_str(fd, "Usage: ");
                    sys_write_str(fd, commands[i].usage);
                    sys_write(fd, "\n", 1);
                } else {
                    commands[i].handler(fd, argc, argv);
                }
                break;
            }
        }

        if (!found) {
            sys_write_str(fd, argv[0]);
            sys_write_str(fd, ": command not found\n");
        }

        print_prompt(fd);
    }
}
