#include <stdlib.h>

int max(int a, int b) { return a > b ? a : b; }

int main() {
  int *mem = calloc(sizeof(int), 1000);
  int *mem2 = calloc(sizeof(int), 100);
  int sum = 0;
  for (int i = 0; i < 1000; i++) {
    mem[i] = i;
    for (int j = 0; j < 100; j++) {
      mem2[j] = j;
      sum += max(mem[i], mem2[j]);
    }
  }
  printf("%d\n", sum);
  if (50116650 != sum) {
    abort();
  }
}
