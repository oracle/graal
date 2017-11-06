#include <stdlib.h>

int main() {
  volatile double *arr = malloc(10 * sizeof(double));
  arr[5] = 1.6;
  arr[4] = 1.45;
  return arr[5] + arr[4];
}
