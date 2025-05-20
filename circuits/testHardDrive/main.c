#define HD_BASE       0x00011000
#define OUT_ADDR      0xFFF00000

volatile int* hd_command = (volatile int*)(HD_BASE);
volatile int* hd_mem_addr = (volatile int*)(HD_BASE + 4);
volatile int* hd_sector = (volatile int*)(HD_BASE + 8);
volatile int* hd_status = (volatile int*)(HD_BASE + 0xC);

#define CMD_READ_SECTOR  1
#define CMD_WRITE_SECTOR 2

#define STATUS_BUSY  0x1
#define STATUS_ERROR 0x2

void printChar(char c) {
    volatile char *p = (volatile char*)OUT_ADDR;
    *p = c;
}

int main(void) {
    volatile int* data = (volatile int*)0x00010000;

    *data = 0xABCDABCD;

    *hd_mem_addr = (int)0x00010000;
    *hd_sector = 0;
    *hd_command = CMD_WRITE_SECTOR;

    while (*hd_status & STATUS_BUSY);


    *data = 0;

    *hd_mem_addr = (int)0x00010000;
    *hd_sector = 1;
    *hd_command = CMD_READ_SECTOR;

    while (*hd_status & STATUS_BUSY);


    if (*data == 0xABCDABCD) {
        printChar('K');
    } else {
        printChar('M');
    }

    return 0;
}