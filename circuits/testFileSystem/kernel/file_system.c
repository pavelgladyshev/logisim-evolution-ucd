

#include "file_system.h"
#include "console.h"
#include "platform.h"
#include "utils.h"


#ifdef WINDOWS
#include <stdio.h>
#include <stdint.h>
static FILE* hd_file = 0;
#else
#include "hardware.h"
#endif



static struct superblock sb;
static unsigned char inode_bitmap[BLOCK_SIZE];
static unsigned char block_bitmap[BLOCK_SIZE];
static struct inode inodes[MAXFILES];

file_entry_t fileTable[MAXFILES];
struct dev_ops driver_table[MAX_MAJOR];
int current_dir_inum = 0;
char current_path[MAX_PATH_LEN];

void print_char(char c) {
    #ifdef WINDOWS
        putchar(c);
    #else
        *OUT_ADDR = c;
    #endif
}
static struct inode* ensure_inode_loaded(int inum) {
    if (inum < 0 || inum >= sb.inodesNumber)
        return 0;
    if (iopen(inum) < 0)
        return 0;
    return &inodes[inum];
}
void print_num(int n) {
    if (n == 0) {
        print_char('0');
        return;
    }
    if (n < 0) {
        print_char('-');
        n = -n;
    }
    if (n >= 10) {
        print_num(n / 10);
    }
    print_char('0' + (n % 10));
}

void print_string(const char* s) {
    platform_print_string(s);
}

void debug_print_sb(void) {
    print_string("DEBUG: superblock: ");
    print_string("inodes="); print_num(sb.inodesNumber);
    print_string(" blocks="); print_num(sb.blocksNumber);
    print_string(" inodeBitmapStart="); print_num(sb.inodeBitmapStart);
    print_string(" blockBitmapStart="); print_num(sb.blockBitmapStart);
    print_string(" inodeStart="); print_num(sb.inodeStart);
    print_string(" dataStart="); print_num(sb.dataStart);
    print_char('\n');
}

void debug_print_inode_loaded(int inum) {
    if (inum < 0 || inum >= sb.inodesNumber) {
        print_string("DEBUG: debug_print_inode invalid inum\n");
        return;
    }
    print_string("DEBUG: inode "); print_num(inum);
    print_string(": size="); print_num(inodes[inum].size);
    print_string(" type="); print_num(inodes[inum].type);
    print_string(" nlink="); print_num(inodes[inum].nlink);
    print_string(" direct0="); print_num(inodes[inum].direct[0]);
    print_char('\n');
}



int fs_chdir(const char *path) {
    if (!path || !*path) {
        print_string("[fs_chdir] invalid path\n");
        return -1;
    }

    char norm[MAX_PATH_LEN];
    normalize_path(path, norm);

    print_string("[fs_chdir] normalized: ");
    print_string(norm);
    print_char('\n');

    int start_inum = (path[0] == '/') ? ROOT_INODE_NUMBER : current_dir_inum;
    int inum = fs_lookup(start_inum, norm);

    if (inum < 0) {
        print_string("[fs_chdir] lookup failed for ");
        print_string(norm);
        print_char('\n');
        return -1;
    }

    struct inode *ip = ensure_inode_loaded(inum);
    if (!ip || ip->type != T_DIR) {
        print_string("[fs_chdir] not a directory: ");
        print_string(norm);
        print_char('\n');
        return -1;
    }

    current_dir_inum = inum;
    strncpy(current_path, norm, MAX_PATH_LEN - 1);
    current_path[MAX_PATH_LEN - 1] = '\0';

    print_string("[fs_chdir] changed directory to ");
    print_string(current_path);
    print_char('\n');

    return 0;
}




void normalize_path(const char *src, char *dst) {
    char full[MAX_PATH_LEN];
    char parts[MAX_PATH_DEPTH][MAX_NAME_LEN];
    int depth = 0;

    if (src[0] == '/') {
        strncpy(full, src, MAX_PATH_LEN - 1);
        full[MAX_PATH_LEN - 1] = '\0';
    } else {
        full[0] = '\0';
        strncpy(full, current_path, MAX_PATH_LEN - 1);
        full[MAX_PATH_LEN - 1] = '\0';
        int len = strlen(full);
        if (len > 0 && full[len - 1] != '/' && len < MAX_PATH_LEN - 1) {
            full[len] = '/';
            full[len + 1] = '\0';
        }
        if (strlen(full) + strlen(src) < MAX_PATH_LEN) {
            strcat(full, src);
        }
    }

    const char *p = full;
    while (*p && depth < MAX_PATH_DEPTH) {
        while (*p == '/') p++;
        if (!*p) break;
        const char *s = p;
        while (*p && *p != '/') p++;
        int len = p - s;
        if (len <= 0) continue;
        if (len >= MAX_NAME_LEN) len = MAX_NAME_LEN - 1;
        char tmp[MAX_NAME_LEN];
        memcpy(tmp, s, len);
        tmp[len] = '\0';

        if (strcmp(tmp, ".") == 0) continue;
        if (strcmp(tmp, "..") == 0) {
            if (depth > 0) depth--;
        } else {
            strcpy(parts[depth], tmp);
            depth++;
        }
    }

    if (depth == 0) {
        strcpy(dst, "/");
        return;
    }

    char *d = dst;
    for (int i = 0; i < depth; i++) {
        *d++ = '/';
        int len = strlen(parts[i]);
        memcpy(d, parts[i], len);
        d += len;
    }
    *d = '\0';
}








int fs_getcwd(char *buf, int size) {
    if (!buf || size <= 0) {
        print_string("[fs_getcwd] invalid buffer or size\n");
        return -1;
    }

    print_string("[fs_getcwd] current_path (global) = ");
    print_string(current_path);
    print_char('\n');

    strncpy(buf, current_path, size);
    buf[size - 1] = '\0';

    print_string("[fs_getcwd] returned path = ");
    print_string(buf);
    print_char('\n');

    print_string("  &current_path = ");
    print_num((unsigned long)current_path);
    print_string("  &buf = ");
    print_num((unsigned long)buf);
    print_char('\n');

    return 0;
}



int fs_get_cwd_inum(void) {
    return current_dir_inum;
}

static int read_block(int blockNumber, void *buf) {
     return platform_read_block(blockNumber, buf);
}

static int write_block(int blockNumber, const void *buf) {
    return platform_write_block(blockNumber, buf);
}

static int allocate_block(void) {
    for (int byte = 0; byte < sb.blocksNumber/8; byte++) {
        unsigned char b = block_bitmap[byte];
        if (b == 0xFF) continue;

        for (int bit = 0; bit < 8; bit++) {
            if (!(b & (1u << bit))) {
                int i = byte * 8 + bit;
                if (i >= sb.blocksNumber) break;
                block_bitmap[byte] = (unsigned char)(b | (1u << bit));
                if (write_block(sb.blockBitmapStart, block_bitmap) < 0) {
                    block_bitmap[byte] = b;
                    print_string("DEBUG: allocate_block: write bitmap failed\n");
                    return -1;
                }
                return i + sb.dataStart;
            }
        }
    }
    return -1;
}


static void free_block(int block) {
    int idx = block - sb.dataStart;
    if (idx < 0 || idx >= sb.blocksNumber) return;
    block_bitmap[idx/8] &= ~(1 << (idx%8));
    unsigned char zero[BLOCK_SIZE] = {0};
    write_block(block, zero);
    write_block(sb.blockBitmapStart, block_bitmap);
}

static int allocate_inode(void) {
    for (int byte = 0; byte < sb.inodesNumber/8; byte++) {
        unsigned char b = inode_bitmap[byte];
        if (b == 0xFF) continue;

        for (int bit = 0; bit < 8; bit++) {
            if (!(b & (1u << bit))) {
                int i = byte * 8 + bit;
                if (i >= sb.inodesNumber) break;
                inode_bitmap[byte] = (unsigned char)(b | (1u << bit));
                if (write_block(sb.inodeBitmapStart, inode_bitmap) < 0) {
                    inode_bitmap[byte] = b;
                    print_string("DEBUG: allocate_inode: write bitmap failed\n");
                    return -1;
                }
                memset(&inodes[i], 0, sizeof(struct inode));
                inodes[i].nlink = 1;
                inodes[i].type  = T_FILE;
                flush_inode(i);
                return i;
            }
        }
    }
    return -1;
}



static void free_inode(int inum) {
    if (inum < 0 || inum >= sb.inodesNumber) return;
    inode_bitmap[inum/8] &= ~(1 << (inum%8));
    memset(&inodes[inum], 0, sizeof(struct inode));
    flush_inode(inum);
    write_block(sb.inodeBitmapStart, inode_bitmap);
}

int flush_inode(int inum) {
    if (inum < 0 || inum >= sb.inodesNumber) {
        if (DEBUG) print_string("DEBUG: flush_inode invalid inum\n");
        return -1;
    }
    int blockOffset = sb.inodeStart + (inum * INODE_SIZE) / BLOCK_SIZE;
    int byteOffset  = (inum * INODE_SIZE) % BLOCK_SIZE;
    unsigned char block[BLOCK_SIZE];
    if (read_block(blockOffset, block) < 0) {
        if (DEBUG) print_string("DEBUG: flush_inode: read_block failed\n");
        return -1;
    }
    memcpy(block + byteOffset, &inodes[inum], sizeof(struct inode));
    if (write_block(blockOffset, block) < 0) {
        if (DEBUG) print_string("DEBUG: flush_inode: write_block failed\n");
        return -1;
    }
    if (DEBUG) {
        print_string("DEBUG: flushed inode ");
        print_num(inum);
        print_string(" to block ");
        print_num(blockOffset);
        print_string(" byteoff ");
        print_num(byteOffset);
        print_string(" size=");
        print_num(inodes[inum].size);
        print_string(" type=");
        print_num(inodes[inum].type);
        print_char('\n');
    }
    return 0;
}



int iopen(int inum) {
    if (inum < 0 || inum >= sb.inodesNumber) {
        if (DEBUG) print_string("DEBUG: iopen invalid inum\n");
        return -1;
    }

    int blockOffset = sb.inodeStart + (inum * INODE_SIZE) / BLOCK_SIZE;
    int byteOffset  = (inum * INODE_SIZE) % BLOCK_SIZE;
    unsigned char block[BLOCK_SIZE];

    if (read_block(blockOffset, block) < 0) {
        if (DEBUG) print_string("DEBUG: iopen read_block failed\n");
        return -1;
    }

    memset(&inodes[inum], 0, sizeof(struct inode));
    memcpy(&inodes[inum], block + byteOffset, sizeof(struct inode));

    if (DEBUG) {
        print_string("DEBUG: iopen loaded inode ");
        print_num(inum);
        print_string(" from block ");
        print_num(blockOffset);
        print_string(" byteoff ");
        print_num(byteOffset);
        print_string(": size=");
        print_num(inodes[inum].size);
        print_string(" type=");
        print_num(inodes[inum].type);
        print_char('\n');
    }

    return 0;
}



void print_inodes(void) {
    print_string("\n=== INODE SUMMARY ===\n");

    if (read_block(sb.inodeBitmapStart, inode_bitmap) < 0) {
        print_string("ERROR: Failed to read inode bitmap\n");
        return;
    }

    for (int inum = 0; inum < sb.inodesNumber; inum++) {
        int byte = inum / 8, bit = inum % 8;

        if (inode_bitmap[byte] & (1 << bit)) {
            if (iopen(inum) < 0) continue;

            struct inode *ip = &inodes[inum];

            print_string("Inode ");
            print_num(inum);
            print_string(": ");

            switch (ip->type) {
                case T_FILE: print_string("FILE"); break;
                case T_DIR: print_string("DIR"); break;
                case T_DEV: print_string("DEV"); break;
                default: print_string("???"); break;
            }

            print_string(" size=");
            print_num(ip->size);
            print_string(" links=");
            print_num(ip->nlink);

            if (ip->type == T_DEV) {
                print_string(" dev=");
                print_num(ip->major);
                print_string(":");
                print_num(ip->minor);
            }

            print_char('\n');
        }
    }
}

int fs_open_inode(int inum) {
    if (iopen(inum) < 0) return -1;
    for (int i = 0; i < MAXFILES; i++) {
        if (fileTable[i].inum == -1) {
            fileTable[i].inum  = inum;
            fileTable[i].flags = O_RDONLY;
            fileTable[i].pos   = 0;
            return i;
        }
    }
    return -1;
}

int fs_lookup(int start_inum, const char *path) {
    if (!path) {
        print_string("[fs_lookup] ERROR: null path\n");
        return -1;
    }

    int current_inum = start_inum;
    const char *p = path;


    if (*p == '/') {
        current_inum = ROOT_INODE_NUMBER;
        while (*p == '/') p++;
    }


    if (*p == '\0') {
        print_string("[fs_lookup] empty path → inum=");
        print_num(current_inum);
        print_char('\n');
        return current_inum;
    }


    char tokens[MAX_PATH_DEPTH][MAX_NAME_LEN];
    int token_count = tokenize_path(p, tokens);
    if (token_count <= 0) {
        print_string("[fs_lookup] tokenize_path failed for ");
        print_string(path);
        print_char('\n');
        return -1;
    }

    print_string("[fs_lookup] start_inum="); print_num(start_inum);
    print_string(" token_count="); print_num(token_count);
    print_char('\n');

    for (int t = 0; t < token_count; t++) {
        const char *name = tokens[t];
        print_string("[fs_lookup] step "); print_num(t);
        print_string(" name='"); print_string(name); print_string("'\n");

        if (strcmp(name, ".") == 0) {
            print_string("[fs_lookup] '.' → stay in current dir ");
            print_num(current_inum); print_char('\n');
            continue;
        }

        if (strcmp(name, "..") == 0) {
            struct inode *dir_inode = ensure_inode_loaded(current_inum);
            if (!dir_inode || dir_inode->type != T_DIR) {
                print_string("[fs_lookup] '..' failed: not a directory\n");
                return -1;
            }

            unsigned char block_data[BLOCK_SIZE];
            if (platform_read_block(dir_inode->direct[0], block_data) < 0) {
                print_string("[fs_lookup] ERROR reading '..' block\n");
                return -1;
            }

            struct dirent *entries = (struct dirent *)block_data;
            int entries_per_block = BLOCK_SIZE / (int)sizeof(struct dirent);
            int parent_inum = current_inum;

            for (int i = 0; i < entries_per_block; i++) {
                if (entries[i].inum && strcmp(entries[i].name, "..") == 0) {
                    parent_inum = entries[i].inum;
                    break;
                }
            }

            print_string("[fs_lookup] '..' → parent_inum=");
            print_num(parent_inum); print_char('\n');
            current_inum = parent_inum;
            continue;
        }

        struct inode *dir_inode = ensure_inode_loaded(current_inum);
        if (!dir_inode || dir_inode->type != T_DIR) {
            print_string("[fs_lookup] ERROR: not a directory @ inum=");
            print_num(current_inum); print_char('\n');
            return -1;
        }

        int found_inum = -1;
        int total_blocks = (dir_inode->size + BLOCK_SIZE - 1) / BLOCK_SIZE;

        for (int b = 0; b < total_blocks && found_inum < 0; b++) {
            int block_num = dir_inode->direct[b];
            if (!block_num) continue;

            unsigned char block_data[BLOCK_SIZE];
            if (platform_read_block(block_num, block_data) < 0) {
                print_string("[fs_lookup] WARN: read_block failed b=");
                print_num(b); print_char('\n');
                continue;
            }

            struct dirent *entries = (struct dirent *)block_data;
            int entries_per_block = BLOCK_SIZE / (int)sizeof(struct dirent);

            for (int i = 0; i < entries_per_block; i++) {
                if (entries[i].inum && strcmp(entries[i].name, name) == 0) {
                    found_inum = entries[i].inum;
                    break;
                }
            }
        }

        if (found_inum < 0) {
            print_string("[fs_lookup] component not found: ");
            print_string(name);
            print_string(" in dir ");
            print_num(current_inum);
            print_char('\n');
            return -1;
        }

        print_string("[fs_lookup] found ");
        print_string(name);
        print_string(" → inum=");
        print_num(found_inum);
        print_char('\n');

        current_inum = found_inum;
    }

    print_string("[fs_lookup] final result inum=");
    print_num(current_inum);
    print_char('\n');
    return current_inum;
}



void truncate_inode(struct inode *ip, int inum) {
    if (!ip || ip->type != T_FILE) return;

    for (int i = 0; i < MAX_NUMBER_OF_BLOCKS_IN_FILE; i++) {
        if (ip->direct[i]) {
            free_block(ip->direct[i]);
            ip->direct[i] = 0;
        }
    }

    ip->size = 0;
    flush_inode(inum);
}


int fs_open(const char *path, int flags) {
    if (!path) {
        print_string("[fs_open] ERROR: null path\n");
        return -1;
    }

    int start_inum = (path[0] == '/') ? ROOT_INODE_NUMBER : current_dir_inum;

    char norm[MAX_PATH_LEN];
    normalize_path(path, norm);

    print_string("[fs_open] path='"); print_string(path);
    print_string("' norm='"); print_string(norm);
    print_string("' start_inum="); print_num(start_inum);
    print_string(" flags="); print_num(flags);
    print_char('\n');

    int inum = fs_lookup(start_inum, path);
    int exists = (inum >= 0);

    if (!exists) {
        if (!(flags & O_CREAT)) {
            print_string("[fs_open] target not found, no O_CREAT\n");
            return -1;
        }

        int parent_inum;
        char name[MAX_NAME_LEN];
        if (resolve_parent_path(path, &parent_inum, name) < 0) {
            print_string("[fs_open] resolve_parent_path failed\n");
            return -1;
        }

        inum = setup_new_inode(T_FILE, 0, 0);
        if (inum < 0) {
            print_string("[fs_open] setup_new_inode failed\n");
            return -1;
        }

        if (add_dirent(parent_inum, name, inum) < 0) {
            print_string("[fs_open] add_dirent failed\n");
            free_inode(inum);
            return -1;
        }

        flush_inode(parent_inum);

        print_string("[fs_open] created new file '");
        print_string(name);
        print_string("' inum="); print_num(inum); print_char('\n');
    }


    struct inode *ip = ensure_inode_loaded(inum);
    if (!ip) {
        print_string("[fs_open] ensure_inode_loaded failed inum=");
        print_num(inum); print_char('\n');
        return -1;
    }


    if (ip->type == T_DIR) {
        int mode = flags & 0x03;
        if (mode != O_RDONLY || (flags & O_TRUNC)) {
            print_string("[fs_open] ERROR: write/trunc on directory inum=");
            print_num(inum); print_char('\n');
            return -1;
        }

        if (flags & O_CREAT) {
            print_string("[fs_open] NOTE: ignoring O_CREAT for existing dir\n");
            flags &= ~O_CREAT;
        }

        print_string("[fs_open] opened directory inum=");
        print_num(inum); print_char('\n');
    }

    int fd = allocate_file_descriptor();
    if (fd < 0) {
        print_string("[fs_open] ERROR: no free file descriptors\n");
        return -1;
    }

    fileTable[fd].inum  = inum;
    fileTable[fd].flags = flags;
    fileTable[fd].pos   = (flags & O_APPEND) ? ip->size : 0;

    print_string("[fs_open] success: inum=");
    print_num(inum);
    print_string(" fd="); print_num(fd);
    print_string(" flags="); print_num(flags);
    print_char('\n');

    return fd;
}





void fs_seek(int fd, int pos) {
    if (!valid_fd(fd)) return;

    if (fileTable[fd].flags & O_APPEND)
        return;

    if (pos < 0) pos = 0;
    fileTable[fd].pos = pos;
}


int fs_init(void) {
    for (int i = 0; i < MAXFILES; i++) fileTable[i].inum = -1;

    if (read_block(SUPERBLOCK_BLOCK, &sb) < 0) {
        print_string("fs_init: failed to read superblock\n"); return -1;
    }
    if (DEBUG) debug_print_sb();

    if (read_block(sb.inodeBitmapStart, inode_bitmap) < 0) {
        print_string("fs_init: failed to read inode bitmap\n"); return -1;
    }
    if (DEBUG) print_string("DEBUG: read inode bitmap\n");

    if (read_block(sb.blockBitmapStart, block_bitmap) < 0) {
        print_string("fs_init: failed to read block bitmap\n"); return -1;
    }
    if (DEBUG) print_string("DEBUG: read block bitmap\n");

    if (iopen(ROOT_INODE_NUMBER) < 0) {
        print_string("fs_init: failed to open root inode\n"); return -1;
    }
    if (DEBUG) debug_print_inode_loaded(ROOT_INODE_NUMBER);

    if (inodes[ROOT_INODE_NUMBER].type != T_DIR) {
        print_string("Correcting root inode type to directory\n");
        inodes[ROOT_INODE_NUMBER].type = T_DIR;
        inodes[ROOT_INODE_NUMBER].nlink = 2;
        flush_inode(ROOT_INODE_NUMBER);
    }
    current_dir_inum = ROOT_INODE_NUMBER;
    strcpy(current_path, "/");
    return 0;
}

int fs_format(void) {
    sb.inodesNumber     = 128;
    sb.blocksNumber     = 1000;
    sb.inodeBitmapStart = 2;
    sb.blockBitmapStart = 3;
    sb.inodeStart       = 4;

    int inodeBlocks = (sb.inodesNumber * INODE_SIZE + BLOCK_SIZE - 1) / BLOCK_SIZE;
    sb.dataStart = sb.inodeStart + inodeBlocks;

    if (write_block(SUPERBLOCK_BLOCK, &sb) < 0) return -1;

    memset(inode_bitmap, 0, BLOCK_SIZE);
    memset(block_bitmap, 0, BLOCK_SIZE);
    if (write_block(sb.inodeBitmapStart, inode_bitmap) < 0) return -1;
    if (write_block(sb.blockBitmapStart, block_bitmap) < 0) return -1;
    inode_bitmap[0] |= 1;
    if (write_block(sb.inodeBitmapStart, inode_bitmap) < 0) return -1;


    memset(&inodes[ROOT_INODE_NUMBER], 0, sizeof(struct inode));
    inodes[ROOT_INODE_NUMBER].type  = T_DIR;
    inodes[ROOT_INODE_NUMBER].nlink = 2; // "." and ".."


    int root_block = allocate_and_clear_block();
    if (root_block <= 0) return -1;
    inodes[ROOT_INODE_NUMBER].direct[0] = root_block;
    inodes[ROOT_INODE_NUMBER].size = 2 * (int)sizeof(struct dirent);
    if (flush_inode(ROOT_INODE_NUMBER) < 0) return -1;


    unsigned char block[BLOCK_SIZE];
    memset(block, 0, BLOCK_SIZE);
    struct dirent *ents = (struct dirent *)block;
    ents[0].inum = ROOT_INODE_NUMBER; strcpy(ents[0].name, ".");
    ents[1].inum = ROOT_INODE_NUMBER; strcpy(ents[1].name, "..");
    if (write_block(root_block, block) < 0) return -1;


    int console_inum = allocate_inode();
    if (console_inum >= 0) {
        struct inode *cip = &inodes[console_inum];
        memset(cip, 0, sizeof(*cip));
        cip->type  = T_DEV;
        cip->nlink = 1;
        cip->major = DEV_CONSOLE;
        cip->minor = 0;
        if (flush_inode(console_inum) < 0) return -1;

        if (read_block(root_block, block) < 0) return -1;
        struct dirent *dp = (struct dirent *)block;


        int per_block = BLOCK_SIZE / (int)sizeof(struct dirent);
        for (int i = 0; i < per_block; i++) {
            if (dp[i].inum == 0) {
                dp[i].inum = console_inum;
                strncpy(dp[i].name, "console", MAX_NAME_LEN - 1);
                dp[i].name[MAX_NAME_LEN - 1] = '\0';
                if (write_block(root_block, block) < 0) return -1;
                inodes[ROOT_INODE_NUMBER].size += (int)sizeof(struct dirent);
                if (flush_inode(ROOT_INODE_NUMBER) < 0) return -1;
                break;
            }
        }
    }

    return 0;
}


int read(int fd, void *buf, int n) {
    if (!valid_fd(fd) || !buf || n < 0)
        return -1;

    file_entry_t *f = &fileTable[fd];

    int mode = f->flags & 0x03;
    if (mode == O_WRONLY)
        return -1;

    struct inode *ip = ensure_inode_loaded(f->inum);
    if (!ip)
        return -1;


    if (ip->type == T_DEV) {
        if (ip->major < 0 || ip->major >= MAX_MAJOR || !driver_table[ip->major].read)
            return -1;
        int count = driver_table[ip->major].read(ip->minor, buf, n, f->pos);
        if (count > 0) f->pos += count;
        return count;
    }


    if (ip->type != T_FILE)
        return -1;

    int total = 0;
    while (total < n && f->pos < ip->size) {
        int blk_index = f->pos / BLOCK_SIZE;
        int blk_off = f->pos % BLOCK_SIZE;
        if (blk_index >= MAX_NUMBER_OF_BLOCKS_IN_FILE)
            break;

        int bnum = ip->direct[blk_index];
        if (!bnum)
            break;

        unsigned char block[BLOCK_SIZE];
        if (platform_read_block(bnum, block) < 0)
            break;

        int to_read = MIN(BLOCK_SIZE - blk_off, n - total);
        if (f->pos + to_read > ip->size)
            to_read = ip->size - f->pos;

        memcpy((unsigned char*)buf + total, block + blk_off, to_read);
        f->pos += to_read;
        total  += to_read;
    }

    return total;
}






int handle_device_write(struct inode *ip, const void *buf, int n, int *pos) {
    if (DEBUG) {
        print_string("[KDEBUG] handle_device_write major=");
        print_num(ip->major);
        print_string(" minor=");
        print_num(ip->minor);
        print_string("\n");
        print_num((int)(uintptr_t)driver_table[ip->major].write);

    }
    if (ip->major < 0 || ip->major >= MAX_MAJOR || !driver_table[ip->major].write)
        return -1;
    int count = driver_table[ip->major].write(ip->minor, buf, n, *pos);
    if (count > 0) *pos += count;
    return count;
}

int write_allowed(int fd) {
    if (!valid_fd(fd))
        return 0;
    int flags = fileTable[fd].flags;
    int mode = flags & 0x03;
    return (mode == O_WRONLY || mode == O_RDWR);
}

int write(int fd, const void *buf, int n) {
    if (!valid_fd(fd)) return -1;
    if (!write_allowed(fd)) return -1;

    file_entry_t *fe = &fileTable[fd];
    struct inode *ip = ensure_inode_loaded(fe->inum);
    if (!ip) return -1;

    if (fe->flags & O_APPEND)
        fe->pos = ip->size;

    if (ip->type == T_DEV)
        return handle_device_write(ip, buf, n, &fe->pos);

    int total_written = 0;
    int remaining = n;
    const char *src = buf;

    while (remaining > 0) {
        int block_index = fe->pos / BLOCK_SIZE;
        int offset_in_block = fe->pos % BLOCK_SIZE;

        if (block_index >= MAX_NUMBER_OF_BLOCKS_IN_FILE)
            break;

        if (ip->direct[block_index] == 0) {
            int new_block = allocate_and_clear_block();
            if (new_block < 0)
                break;
            ip->direct[block_index] = new_block;
        }

        int block_num = ip->direct[block_index];
        int to_write = MIN(BLOCK_SIZE - offset_in_block, remaining);

        write_to_block(block_num, src, offset_in_block, to_write);

        fe->pos += to_write;
        src += to_write;
        remaining -= to_write;
        total_written += to_write;

        if (fe->pos > ip->size)
            ip->size = fe->pos;
    }

    flush_inode(fe->inum);
    return total_written;
}








int write_to_block(int block_num, const void *data, int offset, int size) {
    if (offset < 0 || size <= 0 || offset >= BLOCK_SIZE) return -1;
    if (offset + size > BLOCK_SIZE) size = BLOCK_SIZE - offset;

    unsigned char block[BLOCK_SIZE] = {0};
    if (read_block(block_num, block) < 0)
        memset(block, 0, BLOCK_SIZE);

    memcpy(block + offset, data, size);
    return write_block(block_num, block);
}

int close(int fd) {
    if (fd < 0 || fd >= MAXFILES || fileTable[fd].inum == -1)
        return -1;
    fileTable[fd].inum  = -1;
    fileTable[fd].flags = 0;
    fileTable[fd].pos   = 0;
    return 0;
}





int tokenize_path(const char *path, char tokens[][MAX_NAME_LEN]) {
    if (!path) return -1;
    int count = 0;
    const char *start = path;

    while (*start == '/') start++;

    while (*start && count < MAX_PATH_DEPTH) {
        const char *end = start;
        while (*end && *end != '/') end++;
        int len = end - start;
        if (len >= MAX_NAME_LEN) return -1;
        if (len > 0) {
            strncpy(tokens[count], start, len);
            tokens[count][len] = '\0';
            count++;
        }
        start = (*end) ? end + 1 : end;
        while (*start == '/') start++;
    }
    return count;
}

int init_new_inode(int type, int major, int minor) {
    int inum = allocate_inode();
    if (inum < 0)
        return -1;

    struct inode *ip = &inodes[inum];
    memset(ip, 0, sizeof(*ip));
    ip->type = type;
    ip->major = major;
    ip->minor = minor;
    ip->size  = 0;

    if (flush_inode(inum) < 0) {
        free_inode(inum);
        return -1;
    }
    return inum;
}

int update_dirent(int parent_inum, const char *name, int target_inum, int remove) {
    struct inode *dp = ensure_inode_loaded(parent_inum);
    if (!dp || dp->type != T_DIR) return -1;

    int blocks = (dp->size + BLOCK_SIZE - 1) / BLOCK_SIZE;
    for (int b = 0; b < blocks; b++) {
        int bnum = dp->direct[b];
        if (!bnum) continue;

        unsigned char block[BLOCK_SIZE];
        if (read_block(bnum, block) < 0) continue;

        struct dirent *entries = (struct dirent *)block;
        int per_block = BLOCK_SIZE / (int)sizeof(struct dirent);
        for (int i = 0; i < per_block; i++) {
            if (entries[i].inum == 0) continue;

            if (strcmp(entries[i].name, name) == 0) {
                if (remove) {

                    entries[i].inum = 0;
                    memset(entries[i].name, 0, MAX_NAME_LEN);
                    if (dp->size >= (int)sizeof(struct dirent))
                        dp->size -= (int)sizeof(struct dirent);
                } else {

                    entries[i].inum = target_inum;
                    strncpy(entries[i].name, name, MAX_NAME_LEN - 1);
                    entries[i].name[MAX_NAME_LEN - 1] = '\0';
                }

                flush_inode(parent_inum);
                return write_block(bnum, block);
            }
        }
    }
    return -1;
}

int fs_unlink(const char *path) {
    if (!path) return -1;

    int parent_inum;
    char name[MAX_NAME_LEN];
    if (resolve_parent_path(path, &parent_inum, name) < 0) return -1;

    if (name[0] == '.' && (name[1] == '\0' || (name[1] == '.' && name[2] == '\0')))
        return -1;

    int target_inum = fs_lookup(parent_inum, name);
    if (target_inum < 0) return -1;

    struct inode *ip = &inodes[target_inum];

    if (ip->type == T_DIR) {
        return -1;
    }

    if (update_dirent(parent_inum, name, 0, 1) < 0) return -1;

    if (--ip->nlink <= 0) {
        for (int i = 0; i < MAX_NUMBER_OF_BLOCKS_IN_FILE; i++) {
            if (ip->direct[i]) {
                free_block(ip->direct[i]);
                ip->direct[i] = 0;
            }
        }
        free_inode(target_inum);
    } else {
        flush_inode(target_inum);
    }
    return flush_inode(parent_inum);
}






int fs_mkdir(const char *path) {
    if (!path || !*path) {
        print_string("[fs_mkdir] invalid path\n");
        return -1;
    }

    char norm[MAX_PATH_LEN];
    normalize_path(path, norm);

    int parent_inum;
    char name[MAX_NAME_LEN];

    if (resolve_parent_path(norm, &parent_inum, name) < 0)
        return -1;

    struct inode *parent_ip = ensure_inode_loaded(parent_inum);
    if (!parent_ip || parent_ip->type != T_DIR)
        return -1;

    if (fs_lookup(parent_inum, name) >= 0)
        return -1;

    int inum = setup_new_inode(T_DIR, 0, 0);
    if (inum < 0) return -1;

    struct dirent entries[2];
    entries[0].inum = inum;
    strcpy(entries[0].name, ".");
    entries[1].inum = parent_inum;
    strcpy(entries[1].name, "..");

    int new_block = allocate_and_clear_block();
    if (new_block < 0) return -1;

    if (write_to_block(new_block, entries, 0, sizeof(entries)) < 0)
        return -1;

    struct inode *new_ip = ensure_inode_loaded(inum);
    new_ip->direct[0] = new_block;
    new_ip->size      = sizeof(entries);
    new_ip->nlink     = 2;
    flush_inode(inum);

    if (add_dirent(parent_inum, name, inum) < 0)
        return -1;

    parent_ip = ensure_inode_loaded(parent_inum);
    parent_ip->nlink++;
    flush_inode(parent_inum);

    return 0;
}





int fs_rmdir(const char *path) {
    if (validate_path(path) < 0) {
        print_string("fs_rmdir: invalid path\n");
        return -1;
    }

    char tokens[10][MAX_NAME_LEN];
    int token_count = tokenize_path(path, tokens);
    if (token_count <= 0) {
        print_string("fs_rmdir: tokenize_path failed\n");
        return -1;
    }

    int parent_inum = 0;
    for (int i = 0; i < token_count - 1; i++) {
        parent_inum = fs_lookup(parent_inum, tokens[i]);
        if (parent_inum < 0) {
            print_string("fs_rmdir: parent not found for ");
            print_string(tokens[i]);
            print_string("\n");
            return -1;
        }
    }
    const char *name = tokens[token_count - 1];

    int block_idx, entry_idx;
    if (find_dirent_slot(parent_inum, name, &block_idx, &entry_idx) < 0) {
        print_string("fs_rmdir: dirent not found\n");
        return -1;
    }

    unsigned char block_data[BLOCK_SIZE];
    int block_num = inodes[parent_inum].direct[block_idx];
    if (read_block(block_num, block_data) < 0) {
        print_string("fs_rmdir: read_block failed\n");
        return -1;
    }

    struct dirent *entries = (struct dirent *)block_data;
    struct dirent *entry = &entries[entry_idx];
    int target_inum = entry->inum;

    if (inodes[target_inum].type != T_DIR) {
        print_string("fs_rmdir: not a directory\n");
        return -1;
    }


    int total_entries = inodes[target_inum].size / sizeof(struct dirent);
    int entries_checked = 0;
    int non_empty = 0;
    int block_count = (inodes[target_inum].size + BLOCK_SIZE - 1) / BLOCK_SIZE;

    for (int i = 0; i < block_count; i++) {
        if (!inodes[target_inum].direct[i]) continue;

        unsigned char dir_block[BLOCK_SIZE];
        if (read_block(inodes[target_inum].direct[i], dir_block) < 0) continue;

        struct dirent *dir_entries = (struct dirent *)dir_block;
        int entries_in_block = BLOCK_SIZE / sizeof(struct dirent);
        int entries_to_check = total_entries - entries_checked;
        if (entries_to_check > entries_in_block) {
            entries_to_check = entries_in_block;
        }

        for (int j = 0; j < entries_to_check; j++) {
            if (dir_entries[j].inum != 0) {
                if (strcmp(dir_entries[j].name, ".") != 0 &&
                    strcmp(dir_entries[j].name, "..") != 0) {
                    non_empty = 1;
                    break;
                }
            }
        }
        if (non_empty) break;

        entries_checked += entries_to_check;
        if (entries_checked >= total_entries) break;
    }

    if (non_empty) {
        print_string("fs_rmdir: directory not empty\n");
        return -1;
    }


    entry->inum = 0;
    memset(entry->name, 0, MAX_NAME_LEN);
    if (write_block(block_num, block_data) < 0) {
        print_string("fs_rmdir: write_block failed\n");
        return -1;
    }

    inodes[parent_inum].size -= sizeof(struct dirent);
    inodes[parent_inum].nlink--;
    if (flush_inode(parent_inum) < 0) {
        print_string("fs_rmdir: parent flush failed\n");
        return -1;
    }


    for (int i = 0; i < MAX_NUMBER_OF_BLOCKS_IN_FILE; i++) {
        if (inodes[target_inum].direct[i]) {
            free_block(inodes[target_inum].direct[i]);
        }
    }


    free_inode(target_inum);
    return 0;
}


int find_dirent_slot(int dir_inum, const char *name, int *found_block_index, int *found_entry_index) {
    struct inode *dip = ensure_inode_loaded(dir_inum);
    if (!dip || dip->type != T_DIR) return -1;

    int block_count = (dip->size + BLOCK_SIZE - 1) / BLOCK_SIZE;
    unsigned char block[BLOCK_SIZE];

    for (int blk_idx = 0; blk_idx < block_count; blk_idx++) {
        int block_num = dip->direct[blk_idx];
        if (!block_num) continue;

        if (read_block(block_num, block) < 0) continue;

        int entries_per_block = BLOCK_SIZE / (int)sizeof(struct dirent);
        struct dirent *entries = (struct dirent *)block;

        for (int ent_idx = 0; ent_idx < entries_per_block; ent_idx++) {
            if (entries[ent_idx].inum == 0) continue;
            if (strcmp(entries[ent_idx].name, name) == 0) {
                if (found_block_index) *found_block_index = blk_idx;
                if (found_entry_index) *found_entry_index = ent_idx;
                return 0;
            }
        }
    }
    return -1;
}


int valid_fd(int fd) {
    return (fd >= 0 && fd < MAXFILES && fileTable[fd].inum != -1);
}



int add_dirent(int parent_inum, const char *name, int inum) {
    struct inode *dp = ensure_inode_loaded(parent_inum);
    if (!dp || dp->type != T_DIR) return -1;

    int blocks = (dp->size + BLOCK_SIZE - 1) / BLOCK_SIZE;
    for (int b = 0; b < blocks; b++) {
        int bnum = dp->direct[b];
        if (!bnum) continue;

        unsigned char block[BLOCK_SIZE];
        if (read_block(bnum, block) < 0) continue;

        struct dirent *entries = (struct dirent *)block;
        int per_block = BLOCK_SIZE / (int)sizeof(struct dirent);
        for (int i = 0; i < per_block; i++) {
            if (entries[i].inum == 0) {
                entries[i].inum = inum;
                strncpy(entries[i].name, name, MAX_NAME_LEN - 1);
                entries[i].name[MAX_NAME_LEN - 1] = '\0';
                dp->size += (int)sizeof(struct dirent);
                flush_inode(parent_inum);
                return write_block(bnum, block);
            }
        }
    }

    int new_block = allocate_and_clear_block();
    if (new_block < 0) return -1;
    dp->direct[blocks] = new_block;

    unsigned char block[BLOCK_SIZE] = {0};
    struct dirent *entries = (struct dirent *)block;
    entries[0].inum = inum;
    strncpy(entries[0].name, name, MAX_NAME_LEN - 1);
    entries[0].name[MAX_NAME_LEN - 1] = '\0';

    dp->size += (int)sizeof(struct dirent);
    int rc = write_block(new_block, block);
    flush_inode(parent_inum);
    return rc;
}




int allocate_and_clear_block(void) {
    int block = allocate_block();
    if (block <= 0) return -1;
    unsigned char zero_block[BLOCK_SIZE] = {0};
    if (write_block(block, zero_block) < 0) {
        free_block(block);
        return -1;
    }
    return block;
}

int find_dirent(int dir_inum, const char *name, int *block_index, int *entry_index) {
    struct dirent entries[BLOCK_SIZE / sizeof(struct dirent)];
    int block_count = (inodes[dir_inum].size + BLOCK_SIZE - 1) / BLOCK_SIZE;

    for (int i = 0; i < block_count; i++) {
        if (!inodes[dir_inum].direct[i] || read_block(inodes[dir_inum].direct[i], entries) < 0)
            continue;

        int entries_count = BLOCK_SIZE / sizeof(struct dirent);
        for (int j = 0; j < entries_count; j++) {
            if (entries[j].inum && strcmp(entries[j].name, name) == 0) {
                *block_index = i;
                *entry_index = j;
                return 0;
            }
        }
    }
    return -1;
}

int resolve_parent_path(const char *path, int *parent_inum, char *name) {
    if (!path || !parent_inum || !name) return -1;

    char norm[MAX_PATH_LEN];
    normalize_path(path, norm);

    char tokens[MAX_PATH_DEPTH][MAX_NAME_LEN];
    int token_count = tokenize_path(norm, tokens);
    if (token_count <= 0) return -1;

    *parent_inum = (norm[0] == '/') ? ROOT_INODE_NUMBER : current_dir_inum;  // 🔥 CHANGED

    for (int i = 0; i < token_count - 1; i++) {
        int next = fs_lookup(*parent_inum, tokens[i]);
        if (next < 0) return -1;
        *parent_inum = next;
    }

    strncpy(name, tokens[token_count - 1], MAX_NAME_LEN - 1);
    name[MAX_NAME_LEN - 1] = '\0';
    return 0;
}



int validate_path(const char *path) {
    if (!path) return -1;

    int len = 0;
    int saw_non_slash = 0;
    for (const char *p = path; *p; p++) {
        if (++len >= MAX_PATH_LEN) return -1;
        if (*p != '/') saw_non_slash = 1;
    }
    return saw_non_slash ? 0 : -1;
}

int allocate_file_descriptor() {
    for (int i = 0; i < MAXFILES; i++) {
        if (fileTable[i].inum == -1) {
            fileTable[i].pos = 0;
            fileTable[i].flags = 0;
            return i;
        }
    }
    return -1;
}

int setup_new_inode(int type, int major, int minor) {
    int inum = allocate_inode();
    if (inum < 0) return -1;

    inodes[inum].type = type;
    inodes[inum].nlink = 1;

    if (type == T_DEV) {
        inodes[inum].major = major;
        inodes[inum].minor = minor;
    }

    if (flush_inode(inum) < 0) {
        free_inode(inum);
        return -1;
    }
    return inum;
}


int fs_readdir(int dir_inum, struct dirent *entries, int count) {
    if (dir_inum <= 0) dir_inum = current_dir_inum;
    if (!entries || count <= 0) return -1;

    struct inode *dir = ensure_inode_loaded(dir_inum);
    if (!dir || dir->type != T_DIR) return -1;

    int total = 0;
    int blocks = (dir->size + BLOCK_SIZE - 1) / BLOCK_SIZE;

    for (int b = 0; b < blocks && total < count; b++) {
        int bnum = dir->direct[b];
        if (!bnum) continue;

        unsigned char block[BLOCK_SIZE];
        if (platform_read_block(bnum, block) < 0) continue;

        struct dirent *src = (struct dirent *)block;
        int per_block = BLOCK_SIZE / (int)sizeof(struct dirent);

        for (int i = 0; i < per_block && total < count; i++) {
            if (src[i].inum != 0) {
                memcpy(&entries[total], &src[i], sizeof(struct dirent));
                total++;
            }
        }
    }
    return total;
}




int fs_mknod(const char *path, int major, int minor) {
    if (validate_path(path) < 0) {
        print_string("fs_mknod: invalid path\n");
        return -1;
    }
    if (major < 0 || major >= MAX_MAJOR) {
        print_string("fs_mknod: invalid major number\n");
        return -1;
    }

    char tokens[10][MAX_NAME_LEN];
    int token_count = tokenize_path(path, tokens);
    if (token_count <= 0) {
        print_string("fs_mknod: tokenize_path failed\n");
        return -1;
    }

    int parent_inum = 0;
    for (int i = 0; i < token_count - 1; i++) {
        parent_inum = fs_lookup(parent_inum, tokens[i]);
        if (parent_inum < 0) {
            print_string("fs_mknod: parent not found for ");
            print_string(tokens[i]);
            print_string("\n");
            return -1;
        }
    }

    const char *name = tokens[token_count - 1];
    if (fs_lookup(parent_inum, name) >= 0) {
        print_string("fs_mknod: file already exists\n");
        return -1;
    }

    int inum = setup_new_inode(T_DEV, major, minor);
    if (inum < 0) {
        print_string("fs_mknod: setup_new_inode failed\n");
        return -1;
    }

    if (add_dirent(parent_inum, name, inum) < 0) {
        print_string("fs_mknod: add_dirent failed\n");
        free_inode(inum);
        return -1;
    }

    return inum;
}






int ioctl(int fd, int cmd, void *arg) {
    if (fd < 0 || fd >= MAXFILES || fileTable[fd].inum == -1) {
        print_string("Invalid file descriptor\n");
        return -1;
    }

    struct inode *ip = &inodes[fileTable[fd].inum];

    if (ip->type != T_DEV) {
        print_string("Not a device file\n");
        return -1;
    }

    if (ip->major < 0 || ip->major >= MAX_MAJOR) {
        print_string("Invalid major number\n");
        return -1;
    }

    if (!driver_table[ip->major].ioctl) {
        print_string("IOCTL not supported\n");
        return -1;
    }

    return driver_table[ip->major].ioctl(ip->minor, cmd, arg);
}

int dev_register(int major, struct dev_ops *ops) {
    if (major < 0 || major >= MAX_MAJOR) {
        print_string("dev_register: Invalid major number ");
        print_num(major);
        print_char('\n');
        return -1;
    }

    if (!ops) {
        print_string("dev_register: Null ops pointer\n");
        return -1;
    }

    driver_table[major] = *ops;

    if (DEBUG){
        print_string("Registered driver for major ");
        print_num(major);
        print_char('\n');
    }
    return 0;
}



