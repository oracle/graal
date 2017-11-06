#include <stdio.h>
#include <string.h>

struct test {
  float arr[3];
};

int main() {
  struct test t1 = { { 12.324, 45345234.21, -32423 } };
  struct test t2 = { { 435324, -345345.012134, 324 } };
  t1 = t2;
  t2.arr[2] = -6.3;
  printf("%f %f %f\n", t1.arr[0], t1.arr[1], t1.arr[2]);
  printf("%f %f %f\n", t2.arr[0], t2.arr[1], t2.arr[2]);
}
