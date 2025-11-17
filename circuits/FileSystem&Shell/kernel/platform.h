#pragma once

#ifndef PLATFORM_H
#define PLATFORM_H

#define NUM_CONSOLES 2

// Array of console file descriptors
extern int platform_console_fd[NUM_CONSOLES];

void platform_init(void);
int platform_init_consoles(void);

int platform_get_console_fd(int console_num);
int platform_write_console(int console_num, const char *str);
int platform_write_console_num(int console_num, int num);
int platform_write_all_consoles(const char *str);
int platform_get_console_count(void);
int platform_console_available(int console_num);

int platform_read_block(int block, void *buf);
int platform_write_block(int block, const void *buf);

void platform_print_char(char c);
void platform_print_string(const char *s);
void platform_print_num(int n);

int platform_console_read(int minor, void *buf, int n, int off);
int platform_console_write(int minor, const void *buf, int n, int off);
int platform_create_consoles(void);
int platform_open_consoles(void);
#endif