#include <stdio.h>
#include <stdlib.h>

int values[] = { 5, 3, 2, 4, 1 };

int cmpfunc(const void *a, const void *b) { return (*(int *)a - *(int *)b); }

int main() {
  qsort(values, 5, sizeof(int), cmpfunc);
  for (int i = 0; i < 5; i++) {
    if (values[i] != i + 1) {
      abort();
    }
  }
}
