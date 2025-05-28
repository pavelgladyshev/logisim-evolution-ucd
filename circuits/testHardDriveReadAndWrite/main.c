#define HD_BASE       0x00110000
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
//volatile int* data;
int data[128];
int data2[128];
int canary;

int main(void) {
    data[0] = 0xABCDABCD;

    for(int i = 0; i < 128; ++i){
        data[i] = i;
    }


    *hd_mem_addr = (int) data;
    *hd_sector = 0;
    *hd_command = CMD_WRITE_SECTOR;

    while (*hd_status & STATUS_BUSY);


    canary = 0xdeadbeef;
    *hd_mem_addr = (int) data2;
    *hd_sector = 0;
    *hd_command = CMD_READ_SECTOR;

    while (*hd_status & STATUS_BUSY);

    int test = 1;

    for(int i = 0; i < 128; ++i){
        if(data2[i] != i) test = 0;
    }


    if (test) {
        printChar('+');
    } else {
        printChar('-');
    }

    return 0;
}