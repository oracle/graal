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


A a;

int main() {
    return 0;
}
