#ifndef FC_H
#define FC_H

#define O_RDONLY  0x01
#define O_WRONLY  0x02
#define O_RDWR    0x03
#define O_CREAT   0x04
#define O_TRUNC   0x08
#define O_APPEND  0x10

#define MAX_NUMBER_OF_BLOCKS_IN_FILE   13
#define BLOCK_SIZE     512
#define ROOT_INODE_NUMBER   0
#define MAXFILES  16
#define DIRENT_SIZE 16
#define INODE_SIZE 64

struct inode { // 64 bytes
    int size;
    int direct[MAX_NUMBER_OF_BLOCKS_IN_FILE];
    int nlink;
    int is_dir;
};

struct dirent { // 32 bytes
    int inum;
    char name[28];
};

struct superblock {
    int inodesNumber;
    int blocksNumber;
    int bitmapStart;
    int inodeStart;
    int dataStart;
};

typedef struct {
    int pos;
    int inum;
    int flags;
} file_entry_t;

extern file_entry_t fileTable[MAXFILES];

int  fs_init(void);
int  fs_format(void);
void fs_close(void);

int  iopen(int inum);
int  fs_open_inode(int inum);
void  fs_seek(int fd, int pos);

int  read(int fd, void *buf, int n);
int  write(int fd, const void *buf, int n);
int  close(int fd);

#endif