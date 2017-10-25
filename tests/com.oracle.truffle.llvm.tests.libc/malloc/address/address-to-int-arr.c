#include <stdlib.h>

int main() {
  int val[] = { 1, 2, 3, 4, 5 };
  volatile int **ptr = malloc(sizeof(int *));
  *ptr = &val[1];
  return (*ptr)[2];
}
