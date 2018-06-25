#include <stdio.h>

typedef int vec4 __attribute__ ((vector_size(16)));

__attribute__((constructor)) int test()
{
    vec4 v0 = {0, 1, 2, 3};
    vec4 v1 = {0, 1, 2, 3};
    vec4 v2 = v0 + v1;
    vec4 v3 = v0 * v1;
    vec4 v4 = v3 - v2;
    vec4 v5 = v0 + v1 + v2 + v3 + v4;
    return 0;
}

int main() {
    return 0;
}
