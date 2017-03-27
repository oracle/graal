#include <stdio.h>

struct A {
    A () {
        printf("CONSTRUCT\n");
    }
    A (const A &a) {
        printf("COPY CONSTRUCT\n");
    }
    ~A() {
        printf("DESTRUCT\n");
    }
};

A a;

int main() {
    return 0;
}
