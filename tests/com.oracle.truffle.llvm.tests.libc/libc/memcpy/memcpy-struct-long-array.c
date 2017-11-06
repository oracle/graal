#include <stdio.h>
#include <string.h>

struct test {
  long arr[3];
};

int main() {
  struct test t1 = { { 34242312L, 2, 3 } };
  struct test t2 = { { 4, 5, 645345345L } };
  t1 = t2;
  t2.arr[2] = 89234123123L;
  printf("%ld %ld %ld\n", t1.arr[0], t1.arr[1], t1.arr[2]);
  printf("%ld %ld %ld\n", t2.arr[0], t2.arr[1], t2.arr[2]);
}
