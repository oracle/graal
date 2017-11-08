// Generates uses of @llvm.bswap.v4i32 (vectorised byte swaps)

#include <stdlib.h>
#include <time.h>

#define SWAP(n) (((n) << 24) | (((n) & 0xff00) << 8) | (((n) >> 8) & 0xff00) | ((n) >> 24))

int main(int argc, char **argv) {
    // Avoid LLVM pre-calculating everything at compile time.
    srand(time(0));
    unsigned int x[16];
    for (int i = 0; i < 16; i++) x[i] = rand();
    unsigned int *buf = &x[0];
    for (int i = 0; i < 16; i++) {
        *buf = SWAP(*buf);
        buf++;
    }
    volatile unsigned int y = x[0];
    exit(0);
}