#include "fc.h"
#include <string.h>
#include <stdio.h>

#define HD_BASE 0x00110000
static volatile int* hd_command = (volatile int*)HD_BASE;
static volatile int* hd_mem_addr = (volatile int*)(HD_BASE + 4);
static volatile int* hd_sector = (volatile int*)(HD_BASE + 8);
static volatile int* hd_status = (volatile int*)(HD_BASE + 0xC);

static struct superblock sb;
static unsigned char bitmap[BLOCK_SIZE];
static struct inode inodes[MAXFILES];
static int fdCounter = 0;
file_entry_t fileTable[MAXFILES];

inline static void hd_wait() {
    while (*hd_status & 0x1);
}


static int read_block(int blockNumber, void *buf) {
    if(blockNumber < 0 || blockNumber >= sb.blocksNumber) return -1;
    *hd_mem_addr = (int)buf;
    *hd_sector = blockNumber;
    *hd_command = 1;
    hd_wait();
    return (*hd_status & 0x2) ? -1 : 0;
}

static int write_block(int blockNumber, const void *buf) {
    if(blockNumber >= sb.blocksNumber) return -1;
    *hd_mem_addr = (int)buf;
    *hd_sector = blockNumber;
    *hd_command = 2;
    hd_wait();
    return (*hd_status & 0x2) ? -1 : 0;
}

int fs_open_inode(int inum) {
    if (iopen(inum) < 0) return -1;

    int fd = fdCounter++;
    if (fd >= MAXFILES) return -1;


    fileTable[fd].inum  = inum;
    fileTable[fd].flags = O_RDONLY;
    fileTable[fd].pos   = 0;
    return fd;
}

void fs_seek(int fd, int pos) {
    if (fd >= 0 && fd < MAXFILES && pos >= 0)
        fileTable[fd].pos = pos;
}

int fs_init(void) {
    //memset(&sb, 0, sizeof sb);
    //memset(fileTable, 0, sizeof fileTable);
    fdCounter = 0;

    if (read_block(1, &sb) < 0) return -1;

    if (sb.inodesNumber == 0) return -1;

    if (read_block(sb.bitmapStart, bitmap) < 0) return -1;

    return 0;
}

int fs_format(void) {
    //memset(&sb, 0, sizeof sb);
    //memset(bitmap, 0, sizeof bitmap);
    //memset(inodes, 0, sizeof inodes);
    //memset(fileTable, 0, sizeof fileTable);
    fdCounter = 0;

    sb.inodesNumber = MAXFILES;
    sb.blocksNumber = 1024;
    sb.bitmapStart  = 2;
    sb.inodeStart   = 3;
    sb.dataStart    = 5;
    if (write_block(1, &sb) < 0) return -1;


    for (int i = 0; i < sb.dataStart; i++)
        bitmap[i / 8] |= (1 << (i % 8));

    if (write_block(sb.bitmapStart, bitmap) < 0) return -1;

    unsigned char zeroBlock[BLOCK_SIZE] = {0};
    for (int i = sb.inodeStart; i < sb.dataStart; i++)
        write_block(i, zeroBlock);

    return 0;
}



int iopen(int inum) {
    if(inum < 0 || inum >= sb.inodesNumber) return -1;

    int blockOffset = sb.inodeStart + (inum * INODE_SIZE) / BLOCK_SIZE;
    int byteOffset = (inum * INODE_SIZE) % BLOCK_SIZE;

    unsigned char block[BLOCK_SIZE];
    read_block(blockOffset, block);
    memcpy(&inodes[inum], block + byteOffset, sizeof(struct inode));

    return inum;
}


int read(int fd, void *buf, int n) {
    if (fd < 0 || fd >= fdCounter || !(fileTable[fd].flags & (O_RDONLY|O_RDWR)))
        return -1;
    if (n <= 0) return 0;

    struct inode *ip = &inodes[fileTable[fd].inum]; //pointer to inode copy in ram
    int done = 0, left = n;
    unsigned char block[BLOCK_SIZE];

    while (left > 0 && fileTable[fd].pos < ip->size) {
        int blockIndex = fileTable[fd].pos / BLOCK_SIZE;
        int offset = fileTable[fd].pos % BLOCK_SIZE;
        int chunk = BLOCK_SIZE - offset;

        if (chunk > left) chunk = left;
        if (blockIndex >= MAX_NUMBER_OF_BLOCKS_IN_FILE) break;
        if (read_block(ip->direct[blockIndex], block) < 0) break;

        memcpy(buf, block + offset, chunk);
        buf = (char*)buf + chunk;
        done += chunk;
        left -= chunk;
        fileTable[fd].pos += chunk;
    }
    return done;
}

static int flush_inode(int inum) {
    int offsetBlock = sb.inodeStart + (inum * INODE_SIZE) / BLOCK_SIZE;
    int offsetByte  = (inum * INODE_SIZE) % BLOCK_SIZE;
    unsigned char block[BLOCK_SIZE];
    if (read_block(offsetBlock, block) < 0) return -1;
    memcpy(block + offsetByte, &inodes[inum], sizeof(struct inode));
    return write_block(offsetBlock, block);
}


int write(int fd, const void *buf, int n) {
    if (fd < 0 || fd >= fdCounter || !(fileTable[fd].flags & (O_WRONLY|O_RDWR)))
        return -1;

    if (n <= 0) return 0;

    struct inode *ip = &inodes[fileTable[fd].inum];
    int done = 0, left = n;
    unsigned char block[BLOCK_SIZE];

    while (left > 0) {
        int blockIndex = fileTable[fd].pos / BLOCK_SIZE;
        int offset = fileTable[fd].pos % BLOCK_SIZE;
        int chunk = BLOCK_SIZE - offset;
        if (chunk > left) chunk = left;
        if (blockIndex >= MAX_NUMBER_OF_BLOCKS_IN_FILE) break;

        if (ip->direct[blockIndex] == 0) {
            for (int i = 0; i < sb.blocksNumber - sb.dataStart; i++) {
                if (!(bitmap[i / 8] & (1 << (i % 8)))) {
                    bitmap[i / 8] |= (1 << (i % 8));
                    ip->direct[blockIndex] = i + sb.dataStart;
                    write_block(sb.bitmapStart, bitmap);
                    break;
                }
            }
            if (ip->direct[blockIndex] == 0) break;
        }

        read_block(ip->direct[blockIndex], block);
        memcpy(block + offset, buf, chunk);
        write_block(ip->direct[blockIndex], block);

        buf = (char*)buf + chunk;
        done += chunk;
        left -= chunk;
        fileTable[fd].pos += chunk;
    }

    if (fileTable[fd].pos > ip->size)
        ip->size = fileTable[fd].pos;
    flush_inode(fileTable[fd].inum);

    return done;
}

