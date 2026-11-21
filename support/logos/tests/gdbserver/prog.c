/* The gdb server's test program: a function to stop in, a counter to watch, a string, an ecall, and a word
 * stored in a device register (the Timer's mtimecmp, at its default address; mstatus.MIE is 0, so it raises no
 * interrupt) */
#define MTIMECMP (*(volatile unsigned int *)0x02004000)

volatile int counter;
int squares[8];
const char greeting[] = "hello, gdb";

void do_ecall(void);

__attribute__((noinline)) int square(int x)
{
    return x * x;
}

int main(void)
{
    MTIMECMP = 0x13579bdf;
    for (;;) {
        for (int i = 0; i < 8; i++)
            squares[i] = square(i + counter);
        do_ecall();
        counter++;
    }
}
