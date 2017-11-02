#include <stdlib.h>

int main() {
  volatile short *arr = malloc(10 * sizeof(short));
  arr[5] = 212;
  arr[4] = 43;
  return arr[5] + arr[4];
}
