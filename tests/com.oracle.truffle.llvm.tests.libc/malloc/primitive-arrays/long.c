#include <stdlib.h>

int main() {
  volatile long *arr = malloc(10 * sizeof(long));
  arr[5] = 23;
  arr[4] = 65;
  return arr[5] + arr[4];
}
