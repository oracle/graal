#include <stdlib.h>

int max(int a, int b) { return a > b ? a : b; }

int main() {
  int *mem = calloc(sizeof(int), 10000);
  int *mem2 = calloc(sizeof(int), 10000);
  int sum = 0;
  for (int i = 0; i < 10000; i++) {
    mem[i] = i;
    for (int j = 0; j < 1000; j++) {
      mem2[j] = j;
      sum += max(mem[i], mem2[j]);
    }
  }
  if (-1377941052 != sum) {
    abort();
  }
}
