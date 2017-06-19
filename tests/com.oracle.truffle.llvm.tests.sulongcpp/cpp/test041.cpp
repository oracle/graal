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
    try {
        try {
            try  {
                throw &a;
            } catch (A *e) {
                 printf("C1\n");
                 throw; 
            }
        } catch (A *e) {
            printf("C2\n");
            throw; 
        }
    } catch (A *e) {
        printf("C2\n");
        return 0;
    }
    return -1;
}
