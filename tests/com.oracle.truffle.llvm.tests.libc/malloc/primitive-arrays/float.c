#include <stdlib.h>

int main() {
  volatile float *arr = malloc(10 * sizeof(float));
  arr[5] = 1.3;
  arr[4] = 4.8;
  return arr[5] + arr[4];
}
