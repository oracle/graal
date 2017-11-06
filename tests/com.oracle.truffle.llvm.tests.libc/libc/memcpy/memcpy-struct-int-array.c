#include <stdio.h>
#include <string.h>

struct test {
  int arr[3];
};

int main() {
  struct test t1 = { { 1, 2, 3 } };
  struct test t2 = { { 4, 5, 6 } };
  t1 = t2;
  t2.arr[2] = 42;
  printf("%d %d %d\n", t1.arr[0], t1.arr[1], t1.arr[2]);
  printf("%d %d %d\n", t2.arr[0], t2.arr[1], t2.arr[2]);
}
