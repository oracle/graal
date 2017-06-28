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

int main() {
    try {
        try {
            try  {
                A a;
                throw a;
            } catch (A e) {
                 printf("C1\n");
                 throw e; 
            }
        } catch (A e) {
            printf("C2\n");
            throw e; 
        }
    } catch (A e) {
        printf("C2\n");
        return 0;
    }
    return -1;
}
