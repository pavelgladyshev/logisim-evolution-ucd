#define ARRAY_SIZE 10

int array[ARRAY_SIZE];

void printStr(const char *str) {
    volatile int *p = (volatile int *) 0xFFF00000;
    for (int i = 0; str[i] != '\0'; i++) {
        *p = str[i];
    }
}

void printDigits(int num) {
    volatile int *p = (volatile int *) 0xFFF00000;

    if (num == 0) {
        *p = '0';
        return;
    }

    int digits[10];
    int count = 0;

    for (; num > 0; num /= 10){
        digits[count++] = num % 10;
    }

    for (int i = count - 1; i >= 0; i--){
        *p = '0' + digits[i];
    }
}

int main() {
    for (int i = 0; i < ARRAY_SIZE; i++) {
        array[i] = i + 1;
    }

    int sum = 0;
    for (int i = 0; i < ARRAY_SIZE; i++) {
        sum += array[i];
    }

    printStr("Sum of numbers from 1 to ");
    printDigits(ARRAY_SIZE);
    printStr(" is: ");
    printDigits(sum);
    printStr("\n");
    return 0;
}
