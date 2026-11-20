/* The program that tb_debug's gdb session debugs */
volatile int counter;
int squares[8];

__attribute__((noinline)) int square(int x)
{
    return x * x;
}

int main(void)
{
    for (;;) {
        for (int i = 0; i < 8; i++)
            squares[i] = square(i + counter);
        counter++;
    }
}
