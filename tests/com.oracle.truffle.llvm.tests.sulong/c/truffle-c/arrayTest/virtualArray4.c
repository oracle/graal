#include <stdlib.h>

void fill(int *a) {
  a[0] = 1;
  a[1] = 2;
  a[2] = 3;
}

int main() {
  void (*f)(int *);
  f = fill;

  int *p = calloc(3, sizeof(int));
  (*f)(p);
  return p[0] + p[1] + p[2];
}
