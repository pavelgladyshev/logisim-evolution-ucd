#define MAX_NUM 100
#define OUT_ADDR 0xFFF00000
#define DEBUG_STATS (*(volatile int*) 0xFFF00010)

char is_prime[MAX_NUM + 1];

#include <stdbool.h>

// MMIO base addresses
#define HD_BASE    0x40000000u   // HardDrive lives at 0x40_000000…0x40_FFFFFF
#define UART_BASE  0xFFF00000u   // UART console

// A 32-bit store to HD_BASE+offset => CPU.MEMWRITE + HD_CS => HD.WRITE asserted
// A 32-bit load  from HD_BASE+offset => CPU.MEMREAD  + HD_CS => HD.READ  asserted

// Console output
static inline void printChar(char c) {
    *(volatile unsigned*)UART_BASE = (unsigned)c;
}
static void printStr(const char *s) {
    while (*s) printChar(*s++);
}

// Write one byte to HardDrive at byte-offset 'off'
static inline void writeByteToHD(unsigned off, unsigned char b) {
    // store-byte (SB) to address HD_BASE+off
    *(volatile unsigned char*)(HD_BASE + off) = b;
}

// Read one byte back from HardDrive at byte-offset 'off'
static inline unsigned char readByteFromHD(unsigned off) {
    // load-byte (LB) from address HD_BASE+off
    return *(volatile unsigned char*)(HD_BASE + off);
}

int main(void) {
    unsigned char out = 'X', in;

    printStr("Writing ‘X’ to HD offset 0...\n");
    writeByteToHD(0, out);

    printStr("Reading back from HD offset 0...\n");
    in = readByteFromHD(0);

    printStr("Got back: ");
    printChar((char)in);
    printStr("\nDone.\n");

    while (true) { /* spin */ }
    return 0;
}

void printDigits(int num) {
    volatile int *p = (volatile int *) OUT_ADDR;
    if (num == 0) {
        *p = '0';
        return;
    }

    int digits[10];
    int count = 0;
    while (num > 0) {
        digits[count++] = num % 10;
        num /= 10;
    }
    for (int i = count - 1; i >= 0; i--) {
        *p = '0' + digits[i];
    }
}

int main() {
    for (int i = 2; i <= MAX_NUM; i++) {
        is_prime[i] = 1;
    }

    for (int i = 2; i * i <= MAX_NUM; i++) {
        if (is_prime[i]) {
            for (int j = i * i; j <= MAX_NUM; j += i) {
                is_prime[j] = 0;
            }
        }
    }

    printStr("Primes up to ");
    printDigits(MAX_NUM);
    printStr(":\n");

    for (int i = 2; i <= MAX_NUM; i++) {
        if (is_prime[i]) {
            printDigits(i);
            printStr(" ");
        }
    }

    DEBUG_STATS = 1;
    return 0;
}
