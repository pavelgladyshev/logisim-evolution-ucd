#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <assert.h>

#define BLOCK_SIZE 512
#define MAX_NAME_LEN 28
#define MAXFILES 128
#define INODE_SIZE 64
#define ROOT_INODE_NUMBER 0
#define MAX_NUMBER_OF_BLOCKS_IN_FILE 26


#define T_FILE 0
#define T_DIR  1
#define T_DEV  2


#define DEV_CONSOLE 1

struct superblock {
    int inodesNumber;
    int blocksNumber;
    int inodeBitmapStart;
    int blockBitmapStart;
    int inodeStart;
    int dataStart;
};

struct inode {
    int size;
    short direct[MAX_NUMBER_OF_BLOCKS_IN_FILE];
    short nlink;
    short type;
    short major;
    short minor;
};

struct dirent {
    int inum;
    char name[MAX_NAME_LEN];
};

static struct superblock sb = {
    .inodesNumber = 128,
    .blocksNumber = 1000,
    .inodeBitmapStart = 2,
    .blockBitmapStart = 3,
    .inodeStart = 4,
    .dataStart = 20
};

static uint8_t block_bitmap[BLOCK_SIZE];
static uint8_t inode_bitmap[BLOCK_SIZE];
static struct inode inodes[128];
static FILE* hd_file;


#define CHECK_NULL(ptr, msg) if ((ptr) == NULL) { perror(msg); exit(EXIT_FAILURE); }
#define CHECK_IO(result, msg) if ((result) != 0) { perror(msg); exit(EXIT_FAILURE); }
#define CHECK_ALLOC(ptr, msg) if ((ptr) == NULL) { fprintf(stderr, "Allocation failed: %s\n", msg); exit(EXIT_FAILURE); }


void print_block(int block_num, const char* label) {
    uint8_t block[BLOCK_SIZE];


    long current_pos = ftell(hd_file);


    fseek(hd_file, block_num * BLOCK_SIZE, SEEK_SET);
    fread(block, 1, BLOCK_SIZE, hd_file);

    printf("\n=== %s (Block %d) ===\n", label, block_num);


    for (int i = 0; i < BLOCK_SIZE; i++) {
        if (i % 16 == 0) {
            if (i > 0) {
                printf(" | ");
                for (int j = i - 16; j < i; j++) {
                    if (block[j] >= 32 && block[j] <= 126) {
                        printf("%c", block[j]);
                    } else {
                        printf(".");
                    }
                }
            }
            printf("\n%04X: ", i);
        }
        printf("%02X ", block[i]);
    }


    printf(" | ");
    for (int j = BLOCK_SIZE - 16; j < BLOCK_SIZE; j++) {
        if (block[j] >= 32 && block[j] <= 126) {
            printf("%c", block[j]);
        } else {
            printf(".");
        }
    }
    printf("\n");


    fseek(hd_file, current_pos, SEEK_SET);
}

static void write_block(int block_num, const void* data) {
    CHECK_IO(fseek(hd_file, (long)block_num * BLOCK_SIZE, SEEK_SET), "fseek write_block");
    CHECK_IO(fwrite(data, BLOCK_SIZE, 1, hd_file) != 1 ? -1 : 0, "fwrite write_block");
}

static void read_block(int block_num, void* buf) {
    if (fseek(hd_file, (long)block_num * BLOCK_SIZE, SEEK_SET) != 0) {
        perror("fseek read_block");
        memset(buf, 0, BLOCK_SIZE);
        return;
    }
    size_t got = fread(buf, 1, BLOCK_SIZE, hd_file);
    if (got != BLOCK_SIZE) {
        memset((uint8_t*)buf + got, 0, BLOCK_SIZE - got);
    }
}

static int allocate_block(void) {
    for (int i = 0; i < sb.blocksNumber; i++) {
        int byte = i / 8, bit = i % 8;
        if (!(block_bitmap[byte] & (1 << bit))) {
            block_bitmap[byte] |= (1 << bit);
            write_block(sb.blockBitmapStart, block_bitmap);
            return i + sb.dataStart;
        }
    }
    return -1;
}

static int allocate_inode(void) {
    for (int i = 1; i < sb.inodesNumber; i++) {
        int byte = i / 8, bit = i % 8;
        if (!(inode_bitmap[byte] & (1 << bit))) {
            inode_bitmap[byte] |= (1 << bit);
            write_block(sb.inodeBitmapStart, inode_bitmap);
            inodes[i] = (struct inode){ .nlink = 1, .type = T_FILE };
            return i;
        }
    }
    return -1;
}

static void flush_inode(int inum) {
    assert(inum >= 0 && inum < sb.inodesNumber);
    uint8_t block[BLOCK_SIZE];
    int block_off = sb.inodeStart + (inum * INODE_SIZE) / BLOCK_SIZE;
    int byte_off = (inum * INODE_SIZE) % BLOCK_SIZE;

    read_block(block_off, block);
    memcpy(block + byte_off, &inodes[inum], sizeof(struct inode));
    write_block(block_off, block);
}

static void initialize_disk(void) {
    uint8_t empty[BLOCK_SIZE] = {0};
    for (int i = 0; i < sb.blocksNumber; i++) {
        write_block(i, empty);
    }
}

static void setup_superblock(void) {
    uint8_t sb_block[BLOCK_SIZE] = {0};
    memcpy(sb_block, &sb, sizeof(sb));
    write_block(1, sb_block);
}

static void setup_bitmaps(void) {
    memset(inode_bitmap, 0, BLOCK_SIZE);
    memset(block_bitmap, 0, BLOCK_SIZE);
    inode_bitmap[0] = 1;
    write_block(sb.inodeBitmapStart, inode_bitmap);
    write_block(sb.blockBitmapStart, block_bitmap);

    memset(&inodes[ROOT_INODE_NUMBER], 0, sizeof(struct inode));
    inodes[ROOT_INODE_NUMBER].nlink = 2;
    inodes[ROOT_INODE_NUMBER].type = T_DIR;
}

static void create_root_directory(void) {
    int block_num = allocate_block();
    inodes[ROOT_INODE_NUMBER].direct[0] = block_num;
    inodes[ROOT_INODE_NUMBER].size = 2 * sizeof(struct dirent);
    flush_inode(ROOT_INODE_NUMBER);

    struct dirent dots[2] = {
        {ROOT_INODE_NUMBER, "."},
        {ROOT_INODE_NUMBER, ".."}
    };
    write_block(block_num, dots);
}


static int create_file(const char* filename) {
    int inum = allocate_inode();
    CHECK_ALLOC(inum != -1, "No free inodes");

    int block_num = allocate_block();
    CHECK_ALLOC(block_num != -1, "No free blocks for file");


    memset(&inodes[inum], 0, sizeof(struct inode));
    inodes[inum].size = 0;
    inodes[inum].nlink = 1;
    inodes[inum].type = T_FILE;
    inodes[inum].direct[0] = block_num;
    flush_inode(inum);
    return inum;
}

static int create_device(const char* name, short major, short minor) {
    int inum = allocate_inode();
    CHECK_ALLOC(inum != -1, "No free inodes for device");

    inodes[inum] = (struct inode){
        .type = T_DEV,
        .major = major,
        .minor = minor,
        .nlink = 1
    };
    flush_inode(inum);

    return inum;
}

static void add_directory_entry(int parent_inum, int file_inum, const char* name) {
    struct inode* parent = &inodes[parent_inum];
    uint8_t block[BLOCK_SIZE];
    read_block(parent->direct[0], block);

    struct dirent entry = {file_inum, ""};
    strncpy(entry.name, name, MAX_NAME_LEN - 1);
    entry.name[MAX_NAME_LEN - 1] = '\0';

    memcpy(block + parent->size, &entry, sizeof(entry));
    write_block(parent->direct[0], block);

    parent->size += sizeof(entry);
    flush_inode(parent_inum);


    inodes[file_inum].nlink += 1;
    flush_inode(file_inum);
}

static void copy_file_data(const char* prog_name, int file_inum) {
    FILE* prog = fopen(prog_name, "rb");
    CHECK_NULL(prog, "fopen prog");

    uint8_t buf[BLOCK_SIZE];
    size_t bytes_read;
    int blkidx = 0;


    inodes[file_inum].size = 0;

    while ((bytes_read = fread(buf, 1, BLOCK_SIZE, prog)) > 0) {
        CHECK_ALLOC(blkidx < MAX_NUMBER_OF_BLOCKS_IN_FILE, "File too large");

        if (inodes[file_inum].direct[blkidx] == 0) {
            int new_block = allocate_block();
            CHECK_ALLOC(new_block != -1, "No free blocks during file copy");
            inodes[file_inum].direct[blkidx] = new_block;
        }

        if (bytes_read < BLOCK_SIZE) {
            memset(buf + bytes_read, 0, BLOCK_SIZE - bytes_read);
        }

        write_block(inodes[file_inum].direct[blkidx], buf);
        inodes[file_inum].size += (int)bytes_read;
        blkidx++;
    }


    fseek(prog, 0, SEEK_END);
    long real_size = ftell(prog);
    if (real_size >= 0) inodes[file_inum].size = (int)real_size;

    flush_inode(file_inum);
    fclose(prog);
}


int main(int argc, char* argv[]) {
    if (argc != 3) {
        fprintf(stderr, "Usage: %s prog.bin harddrive.bin\n", argv[0]);
        return EXIT_FAILURE;
    }

    hd_file = fopen(argv[2], "wb+");
    CHECK_NULL(hd_file, "fopen hd");

    printf("Initializing disk...\n");
    initialize_disk();

    printf("Setting up superblock...\n");
    setup_superblock();

    printf("Setting up bitmaps...\n");
    setup_bitmaps();


    printf("Creating root directory...\n");
    create_root_directory();

    printf("Creating console devices...\n");
    int console0_inum = create_device("console0", DEV_CONSOLE, 0);
    int console1_inum = create_device("console1", DEV_CONSOLE, 1);
    add_directory_entry(ROOT_INODE_NUMBER, console0_inum, "console0");
    add_directory_entry(ROOT_INODE_NUMBER, console1_inum, "console1");

    printf("Creating shell executable...\n");
    int shell_inum = create_file("shell");
    add_directory_entry(ROOT_INODE_NUMBER, shell_inum, "shell");
    copy_file_data(argv[1], shell_inum);

    printf("\nPrinting filesystem structure...\n\n");

    print_block(1, "Superblock");
    print_block(2, "Inode Bitmap");
    print_block(3, "Block Bitmap");


    int inode_blocks = (sb.inodesNumber * INODE_SIZE + BLOCK_SIZE - 1) / BLOCK_SIZE;
    for (int i = 0; i < inode_blocks; i++) {
        char label[64];
        snprintf(label, sizeof(label), "Inode Table Block %d", i);
        print_block(sb.inodeStart + i, label);
    }


    print_block(inodes[ROOT_INODE_NUMBER].direct[0], "Root Directory Entries");


    printf("\nListing device entries:\n");
    printf("  console0 (major=%d, minor=%d)\n",
           inodes[console0_inum].major, inodes[console0_inum].minor);
    printf("  console1 (major=%d, minor=%d)\n",
           inodes[console1_inum].major, inodes[console1_inum].minor);


    printf("\nShell file blocks:\n");
    for (int i = 0; i < MAX_NUMBER_OF_BLOCKS_IN_FILE; i++) {
        if (inodes[shell_inum].direct[i] != 0) {
            char label[64];
            snprintf(label, sizeof(label), "Shell Data Block %d", i);
            print_block(inodes[shell_inum].direct[i], label);
        }
    }

    printf("\nFilesystem structure dump complete.\n");

    fclose(hd_file);
    printf("Filesystem image created successfully: %s\n", argv[2]);
    return EXIT_SUCCESS;
}
