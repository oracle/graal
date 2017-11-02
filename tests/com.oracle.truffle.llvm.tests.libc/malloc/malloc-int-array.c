#include <stdio.h>
#include <stdlib.h>

int main() {
  int *arr = malloc(sizeof(int) * 5);
  volatile int i;
  int sum = 0;
  for (i = 0; i < 5; i++) {
    arr[i] = i;
  }
  for (i = 0; i < 5; i++) {
    sum += arr[i];
  }
  free(arr);
  return sum;
}
