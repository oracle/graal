#include <stdlib.h>

int main() {
  int asdf = 3;
  int *a = &asdf;
  int *b = &asdf;
  int **val = calloc(2, sizeof(int *));
  val[0] = a;
  val[1] = b;
  asdf = 8;
  if (*(val[0]) != 8) {
    abort();
  }
  if (*(val[1]) != 8) {
    abort();
  }
}
