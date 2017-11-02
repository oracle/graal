#include <stdlib.h>

int main() {
  int val = 3;
  volatile int **ptr = malloc(sizeof(int *));
  *ptr = &val;
  return **ptr;
}
