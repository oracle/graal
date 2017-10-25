#include <stdlib.h>

int main() {
  int a = 3;
  int b = 2;
  int c = 8;
  int **arr = malloc(sizeof(int *) * 3);
  arr[0] = &a;
  arr[1] = &b;
  arr[2] = &c;
  int sum = 0;
  for (int i = 0; i < 3; i++) {
    sum += *(arr[i]);
  }
  free(arr);
  return sum;
}
