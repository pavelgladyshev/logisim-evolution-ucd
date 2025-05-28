#include "fc.h"
#include <string.h>

#define OUT_ADDR 0xFFF00000

void print(char c) {
    *(volatile char*)OUT_ADDR = c;
}

int main() {
      print('+');
      if (fs_format() < 0) {
         print('F');
         return -1;
     }
     if (fs_init() < 0) {
         print('I');
         return -1;
     }
     if (iopen(1) < 0) {
         print('O');
         return -1;
     }


     int fd = fs_open_inode(1);
     if (fd < 0) {
         print('E');
         return -1;
     }

     fileTable[fd].flags = O_RDWR;
     const char *msg = "Hello";
     int w = write(fd, msg, strlen(msg));
     if (w != strlen(msg)) {
         print('W');
         return -1;
     }
     fs_seek(fd, 0);

     char buf[128] = {0};
     int n = read(fd, buf, sizeof(buf));
     if (n < 0) {
         print('R');
         return -1;
     } else if (n == 0) {
         print('Z');
         return 0;
     }

     for (int i = 0; i < n; i++) {
         print(buf[i]);
     }

     return 0;
}
