#include "utils.h"
#ifndef WINDOWS
int strlen(const char *s) {


    int len = 0;
    while (*s++) len++;
    return len;
}

int strcmp(const char *a, const char *b) {
    while (*a && (*a == *b)) {
        a++; b++;
    }
    return *(unsigned char *)a - *(unsigned char *)b;
}

int strncmp(const char *a, const char *b, unsigned int n) {
    while (n && *a && (*a == *b)) {
        a++;
        b++;
        n--;
    }
    if (n == 0)
        return 0;
    return *(const unsigned char *)a - *(const unsigned char *)b;
}

char *strcat(char *dest, const char *src) {
    char *d = dest;
    while (*d)
        d++;
    while ((*d++ = *src++));

    return dest;
}



void *memset(void *s, int c, unsigned int n) {
    unsigned char *p = s;
    while (n--) *p++ = (unsigned char)c;
    return s;
}

int memcmp(const void *s1, const void *s2, unsigned int n) {
    const unsigned char *p1 = s1;
    const unsigned char *p2 = s2;

    while (n--) {
        if (*p1 != *p2)
            return *p1 - *p2;
        p1++;
        p2++;
    }
    return 0;
}

void *memcpy(void *dest, const void *src, unsigned int n) {
    unsigned char *d = dest;
    const unsigned char *s = src;
    if (n >= 4 && !(((unsigned int)dest | (unsigned int)src) & 3)) {
        unsigned int nwords = n >> 2;
        unsigned int *dword = (unsigned int *)d;
        const unsigned int *sword = (const unsigned int *)s;

        while (nwords--) {
            *dword++ = *sword++;
        }

        d = (unsigned char *)dword;
        s = (const unsigned char *)sword;
        n &= 3;
    }

    while (n--) {
        *d++ = *s++;
    }
    return dest;
}

char *strncpy(char *dest, const char *src, int n) {
    int i;
    for (i = 0; i < n && src[i]; i++) {
        dest[i] = src[i];
    }
    for (; i < n; i++) {
        dest[i] = '\0';
    }
    return dest;
}


char *strcpy(char *dest, const char *src) {
    char *d = dest;
    while ((*d++ = *src++)) {
        ; // copy including NUL
    }
    return dest;
}

char *strtok(char *str, const char *delim) {
    static char *next;
    if (str) next = str;

    if (!next) return 0;

    char *start = next;
    while (*start && strchr(delim, *start))
        start++;

    if (!*start) {
        next = 0;
        return 0;
    }

    char *end = start;
    while (*end && !strchr(delim, *end))
        end++;

    if (*end) {
        *end = '\0';
        next = end + 1;
    } else {
        next = 0;
    }

    return start;
}

char *strchr(const char *s, int c) {
    while (*s) {
        if (*s == (char)c)
            return (char *)s;
        s++;
    }
    return (c == 0) ? (char *)s : 0;
}

char *strncat(char *dest, const char *src, int n) {
    char *d = dest;
    while (*d) d++;  // move to end of dest

    while (n-- > 0 && *src) {
        *d++ = *src++;
    }
    *d = '\0';
    return dest;
}

char *strrchr(const char *s, int c) {
    const char *last = 0;
    while (*s) {
        if (*s == (char)c)
            last = s;
        s++;
    }
    return (char *)last;
}

char *itoa(int value, char *str, int base) {
    char digits[] = "0123456789ABCDEF";
    char temp[32];
    int i = 0;
    int j = 0;
    int negative = 0;

    if (value == 0) {
        str[0] = '0';
        str[1] = '\0';
        return str;
    }

    if (value < 0 && base == 10) {
        negative = 1;
        value = -value;
    }

    while (value > 0 && i < (int)(sizeof(temp) - 1)) {
        temp[i++] = digits[value % base];
        value /= base;
    }

    if (negative)
        temp[i++] = '-';

    while (i > 0) {
        str[j++] = temp[--i];
    }
    str[j] = '\0';
    return str;
}

#endif