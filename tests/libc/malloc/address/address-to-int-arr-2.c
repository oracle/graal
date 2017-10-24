#include <stdlib.h>

int main() {
  volatile int val[] = { 1, 2, 3, 4, 5 };
  volatile int **ptr = malloc(sizeof(int *));
  *ptr = &val;
  return (*ptr)[2];
}
