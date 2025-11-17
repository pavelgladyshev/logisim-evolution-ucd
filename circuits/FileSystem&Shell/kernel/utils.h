#ifndef UTILS_H
#define UTILS_H

#ifdef HOST_BUILD
#include <string.h>
#else
int strlen(const char *s);
int strcmp(const char *a, const char *b);
void *memset(void *s, int c, unsigned int n);
void *memcpy(void *dest, const void *src, unsigned int n);
char *strncpy(char *dest, const char *src, int n);
int strncmp(const char *a, const char *b, unsigned int n);
int memcmp(const void *s1, const void *s2, unsigned int n);
char *strcpy(char *dest, const char *src);
char *strchr(const char *s, int c);
char *strchr(const char *s, int c);
char *strtok(char *str, const char *delim);
char *strncat(char *dest, const char *src, int n);
char *itoa(int value, char *str, int base);
char *strcat(char *dest, const char *src);
#endif

#endif
