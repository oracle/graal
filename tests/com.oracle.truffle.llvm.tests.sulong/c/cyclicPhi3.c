#include <stdio.h>

volatile int start = 0;
volatile int end = 15;

volatile int a = 42;
volatile int b = 66;

int main() {



    for (int i = start; i < end; i++) {
        printf("%d %d\n", a, b);
		int tmp = a;
        a = b;
        b = tmp;
    }
    return 0;
}
