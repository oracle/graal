#include <stdlib.h>
#define size 10

int main() {
  int sum;
  int *int_p = (int *)malloc(size * sizeof(int));
  void *void_p;
  void_p = int_p;
  int i;
  for (i = 0; i < size; i++) {
    int *int_p2 = (int *)void_p;
    sum += *int_p + *int_p2;
    int_p++;
    void_p = (void *)((int)void_p + sizeof(int));
  }
  return sum;
}
