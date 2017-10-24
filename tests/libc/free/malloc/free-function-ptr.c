#include <stdlib.h>

int add(int a, int b) { return a + b; }

int main() {
  int (**test)(int, int) = malloc(sizeof(int (*)(int, int)));
  *test = &add;
  int result = (*test)(5, 7);
  free(test);
  return result;
}
