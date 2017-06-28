#include <stdio.h>

struct A {
    A () {
        printf("CONSTRUCT A\n");
    }
    A (const A &a) {
        printf("COPY CONSTRUCT A\n");
    }
    ~A() {
        printf("DESTRUCT A\n");
    }
};

struct B {
    B () {
        printf("CONSTRUCT B\n");
    }
    B (const B &a) {
        printf("COPY CONSTRUCT B\n");
    }
    ~B() {
        printf("DESTRUCT B\n");
    }
};

A a;
B b;

int main() {
    return 0;
}
