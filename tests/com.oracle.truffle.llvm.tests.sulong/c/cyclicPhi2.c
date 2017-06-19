#include <stdio.h>

volatile int start = 0;
volatile int end = 15;

int main() {

    const char *a = "bla";
    const char *b = "blub";

    for (int i = start; i < end; i++) {
        printf("%s %s\n", a, b);
        const char *tmp = a;
        a = b;
        b = tmp;
    }
    return 0;
}