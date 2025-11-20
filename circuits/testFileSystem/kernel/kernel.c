#include "console.h"
#include "trap.h"
#include "file_system.h"
#include "utils.h"
#include "platform.h"

#define SHELL_LOAD_ADDR 0x0001B000
#define SHELL_STACK_ADDR 0x0001F000   // 4KB stack at the top of the 20KB region
#define READ_CHUNK_SIZE 512

static trap_frame_t shell_tf;

static void print_byte_hex(uint8_t val) {
    const char hex_chars[] = "0123456789ABCDEF";
    char buf[3];
    buf[0] = hex_chars[(val >> 4) & 0xF];
    buf[1] = hex_chars[val & 0xF];
    buf[2] = '\0';
    print_string(buf);
}


static int load_executable(const char *path, uint32_t load_address) {
    print_string("=== Load executable start ===\n");

    int fd = fs_open(path, O_RDONLY);

    fileTable[fd].pos = 0;


    int total_read = 0;
    uint8_t *load_ptr = (uint8_t *)load_address;
    int bytes_read;

    print_string("Loading into RAM at address ");
    print_num(load_address);


    uint8_t buffer[READ_CHUNK_SIZE];
    while ((bytes_read = read(fd, buffer, READ_CHUNK_SIZE)) > 0) {

        memcpy(load_ptr, buffer, (unsigned int)bytes_read);

        load_ptr += bytes_read;

        total_read += bytes_read;

        print_string("Read ");
        print_num(bytes_read);
        print_string(" bytes, wrote to ");
        print_num((uint32_t)(load_ptr - bytes_read));
        print_string("\n");
    }


    close(fd);

    print_string("=== Load executable end ===, total bytes = ");
    print_num(total_read);
    print_string("\n");



    print_string("Dump first 16 bytes: ");
    for (int i = 0; i < 16 && i < total_read; i++) {
        print_byte_hex(((uint8_t*)load_address)[i]);
        print_string(" ");
    }
    print_string("\n");

    return total_read;
}

static void init_consoles(void) {
    if (fs_open("/console0", O_RDWR) < 0) {
        fs_mknod("/console0", DEV_CONSOLE, 0);
    }
    if (fs_open("/console1", O_RDWR) < 0) {
        fs_mknod("/console1", DEV_CONSOLE, 1);
    }
}

static int setup_shell(void) {
    int shell_size = load_executable("/shell", SHELL_LOAD_ADDR);
    if (shell_size <= 0) {
        print_string("Failed to load shell\n");
        return -1;
    }

    print_string("Shell loaded successfully! bytes=");
    print_num(shell_size);
    print_string("\n");


    memset(&shell_tf, 0, sizeof(shell_tf));

    uintptr_t kernel_sp;
    asm volatile ("mv %0, sp" : "=r"(kernel_sp));
    shell_tf.c_trap_sp = kernel_sp;


    shell_tf.mepc = (uintptr_t)SHELL_LOAD_ADDR;
    shell_tf.sp = (uintptr_t)(SHELL_STACK_ADDR & ~((uintptr_t)0xF));
    shell_tf.c_trap = (uintptr_t)c_trap_handler;


    print_string("DEBUG: shell_tf.mepc = ");
    print_num((uintptr_t)shell_tf.mepc);
    print_string("\n");

    print_string("DEBUG: shell_tf.sp = ");
    print_num((uintptr_t)shell_tf.sp);
    print_string("\n");

    print_string("DEBUG: shell_tf.c_trap = ");
    print_num((uintptr_t)shell_tf.c_trap);
    print_string("\n");

    shell_tf.pid = 0;

    return 0;
}



void main(void) {
print_num(sizeof(struct inode));

    platform_print_string("Kernel starting...\n");

    if (fs_init() < 0) {
        platform_print_string("Failed to initialize filesystem\n");
        return;
    }
    console_init();

    if (platform_open_consoles() < 0) {
        platform_print_string("Warning: some consoles failed to open\n");
    }

    print_string("Kernel initialization complete\n");

    int fd = fs_open("/shell", O_RDWR);




    if (setup_shell() == 0) {
        print_string("Setting trap handler and starting shell...\n");
        set_trap_handler(trap_handler, &shell_tf);

        trap_ret(&shell_tf);
    }

    print_string("Kernel finished.\n");

    while (1) {
    }
}