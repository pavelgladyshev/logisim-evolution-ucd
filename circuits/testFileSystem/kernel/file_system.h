#ifndef FC_H
#define FC_H

#include <stdint.h>
#include "utils.h"
#if defined(_WIN32) || defined(__linux__)
#endif

#define O_RDONLY  0x00
#define O_WRONLY  0x01
#define O_RDWR    0x02
#define O_CREAT   0x04
#define O_TRUNC   0x08
#define O_APPEND  0x10


#define MAX_NUMBER_OF_BLOCKS_IN_FILE   26
#define BLOCK_SIZE     512
#define INODE_SIZE     64
#define ROOT_INODE_NUMBER   0
#define MAXFILES  128
#define MAX_NAME_LEN 28
#define MAX_PATH_LEN 256
#define DEV_CONSOLE 1
#define MAX_MAJOR 8
#define DEV_DISPLAY 2
#define DEV_KEYBOARD 3
#define DEBUG 0

#define SUPERBLOCK_BLOCK 1
#define INODE_BITMAP_BLOCK 2
#define BLOCK_BITMAP_BLOCK 3
#define INODE_TABLE_START_BLOCK 4
#define ROOT_DIR_ENTRIES 2
#define EMPTY_BLOCK 0

#define MAX_PATH_DEPTH 10
#define NDIRECT 12

#ifndef MIN
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#endif

struct dev_ops {
    int (*read)(int minor, void *buf, int n, int off);
    int (*write)(int minor, const void *buf, int n, int off);
    int (*ioctl)(int minor, int cmd, void *arg);
};

enum inode_type { T_FILE, T_DIR, T_DEV };

struct inode { //64 bytes
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

struct superblock {
    int inodesNumber;
    int blocksNumber;
    int inodeBitmapStart;
    int blockBitmapStart;
    int inodeStart;
    int dataStart;
};

typedef struct {
    int pos;
    int inum;
    int flags;
} file_entry_t;

extern file_entry_t fileTable[MAXFILES];
extern struct dev_ops driver_table[MAX_MAJOR];
extern int  current_dir_inum;
extern char current_path[MAX_PATH_LEN];

int  fs_init(void);
int  fs_format(void);
int  fs_open(const char *path, int flags);
void fs_seek(int fd, int pos);
int fs_readdir(int dir_inum, struct dirent *entries, int count);
int  read(int fd, void *buf, int n);
int  write(int fd, const void *buf, int n);
int  close(int fd);
int  fs_unlink(const char *path);
int  fs_mkdir(const char *path);
int  fs_rmdir(const char *path);
int  fs_mknod(const char *path, int major, int minor);
int  ioctl(int fd, int cmd, void *arg);
int  dev_register(int major, struct dev_ops *ops);
void init_builtin_drivers();
void print_string(const char* s);
void print_num(int n);
void console_init(void);
int tokenize_path(const char *path, char tokens[][MAX_NAME_LEN]);
int setup_new_inode(int type, int major, int minor);
int allocate_file_descriptor();
int validate_path(const char *path);
int find_dirent_slot(int dir_inum, const char *name, int *found_block_index, int *found_entry_index);
int allocate_and_clear_block();
int add_dirent(int parent_inum, const char *name, int inum);
int update_dirent(int parent_inum, const char *name, int target_inum, int remove);
int init_new_inode(int type, int major, int minor);
int dev_operation(int fd, void *buf, int n, int is_write);
int resolve_parent_path(const char *path, int *parent_inum, char *name);
int write_to_block(int block_num, const void *data, int offset, int size);
int handle_device_write(struct inode *ip, const void *buf, int n, int *pos);
int valid_fd(int fd);
int write_allowed(int fd);
int fs_open_inode(int inum);
void print_inodes(void);
void debug_print_inode_loaded(int inum);
void debug_print_sb(void);
int fs_chdir(const char *path);
int fs_getcwd(char *buf, int size);
int fs_get_cwd_inum(void);
void normalize_path(const char *src, char *dst);
void truncate_inode(struct inode *ip, int inum);
int iopen(int inum);
int fs_lookup(int start_inum, const char *path);
int flush_inode(int inum);

#endif
