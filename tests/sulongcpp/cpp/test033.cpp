#include <stdio.h>

int main() {
    try {
        try {
            try  {
                throw 42;
            } catch (int e) {
                 printf("C1 %d", e);
                 throw; 
            }
        } catch (int e) {
            printf("C2 %d", e);
            throw; 
        }
    } catch (int e) {
        printf("C2 %d", e);
        return e;
    }
    return -1;
}
