#include <stdlib.h>

int main() {
  volatile int *arr = malloc(10 * sizeof(int));
  arr[5] = 12;
  arr[4] = 43;
  return arr[5] + arr[4];
}
