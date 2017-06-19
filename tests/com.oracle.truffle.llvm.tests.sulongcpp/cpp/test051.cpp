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
