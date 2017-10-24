#include <stdlib.h>

int main() {
  volatile int *i = malloc(sizeof(int));
  *i = 34534534;
  return *i % 256;
}
