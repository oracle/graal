#include <stdio.h>
#include "../avx2fallback.h"

int avx2_fallback() {
  printf("v3: -1 -1 -1 -1\n");
  printf("v4: -5 2 8 -1\n");
  return 0;
}

typedef int vec4 __attribute__((vector_size(16)));

int main() {
  volatile vec4 v1 = { -1, 8, 2, -5 };

  volatile vec4 v3 = __builtin_shufflevector(v1, v1, 0, 0, 0, 0);
  printf("v3: %d %d %d %d\n", v3[0], v3[1], v3[2], v3[3]);

  volatile vec4 v4 = __builtin_shufflevector(v1, v1, 3, 2, 1, 0);
  printf("v4: %d %d %d %d\n", v4[0], v4[1], v4[2], v4[3]);

  return 0;
}
