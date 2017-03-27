#include <stdio.h>

int i = 0;

struct A {
    A () {
        printf("CONSTRUCT %d \n", i++);
    }
    A (const A &a) {
        printf("COPY CONSTRUCT %d \n", i++);
    }
    ~A() {
        printf("DESTRUCT %d \n", i++);
    }
};


struct B {
    B () {
        printf("CONSTRUCT B %d \n", i++);
    }
    B (const B &a) {
        printf("COPY CONSTRUCT B %d \n", i++);
    }
    ~B() {
        printf("DESTRUCT B %d \n", i++);
    }
};

A a;
B b;

int main() {
    return 0;
}
