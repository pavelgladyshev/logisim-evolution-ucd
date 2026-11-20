/* OpenOCD's remote_bitbang for an Icarus Verilog test bench: $rbb_poll(port, tck, tms, tdi, tdo) takes the next
 * command from OpenOCD, if one has come: '0'-'7' set tck, tms and tdi (4, 2, 1), and return; 'R' sends tdo;
 * 'Q' (or OpenOCD going) ends the simulation; the others ('B', 'b', 'r'-'u') are dropped. The first call
 * listens on the port (on 127.0.0.1) and waits for OpenOCD. */
#include <vpi_user.h>
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

static int client = -1;

static void put_bit(vpiHandle h, int v) {
    s_vpi_value val = { vpiIntVal };
    val.value.integer = v;
    vpi_put_value(h, &val, NULL, vpiNoDelay);
}

static PLI_INT32 rbb_poll(PLI_BYTE8 *data) {
    (void)data;
    vpiHandle call = vpi_handle(vpiSysTfCall, NULL), args = vpi_iterate(vpiArgument, call);
    vpiHandle hPort = vpi_scan(args), hTck = vpi_scan(args), hTms = vpi_scan(args), hTdi = vpi_scan(args),
              hTdo = vpi_scan(args);
    vpi_free_object(args);
    if (client < 0) {
        s_vpi_value val = { vpiIntVal };
        vpi_get_value(hPort, &val);
        int s = socket(AF_INET, SOCK_STREAM, 0), one = 1;
        setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
        struct sockaddr_in a = { .sin_family = AF_INET, .sin_port = htons(val.value.integer),
                                 .sin_addr.s_addr = htonl(INADDR_LOOPBACK) };
        if (bind(s, (struct sockaddr *)&a, sizeof a) || listen(s, 1)) { perror("rbb"); vpi_control(vpiFinish, 1); return 0; }
        vpi_printf("rbb: waiting for OpenOCD on port %d\n", val.value.integer);
        client = accept(s, NULL, NULL);
        close(s);
        setsockopt(client, IPPROTO_TCP, TCP_NODELAY, &one, sizeof one);
        fcntl(client, F_SETFL, O_NONBLOCK);
        vpi_printf("rbb: OpenOCD connected\n");
    }
    for (;;) {
        char c;
        ssize_t n = read(client, &c, 1);
        if (n == 0) { vpi_printf("rbb: OpenOCD went\n"); vpi_control(vpiFinish, 0); return 0; }
        if (n < 0) return 0;                                  /* nothing yet: the simulation goes on */
        if (c >= '0' && c <= '7') {
            put_bit(hTck, (c - '0') >> 2 & 1); put_bit(hTms, (c - '0') >> 1 & 1); put_bit(hTdi, (c - '0') & 1);
            return 0;
        }
        if (c == 'R') {
            s_vpi_value val = { vpiIntVal };
            vpi_get_value(hTdo, &val);
            char r = val.value.integer ? '1' : '0';
            while (write(client, &r, 1) != 1 && errno == EAGAIN) {}
        } else if (c == 'Q') { vpi_control(vpiFinish, 0); return 0; }
    }
}

static void rbb_register(void) {
    s_vpi_systf_data tf = { vpiSysTask, 0, "$rbb_poll", rbb_poll, NULL, NULL, NULL };
    vpi_register_systf(&tf);
}
void (*vlog_startup_routines[])(void) = { rbb_register, NULL };
