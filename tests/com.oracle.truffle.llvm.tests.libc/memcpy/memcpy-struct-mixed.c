#include <stdio.h>
#include <string.h>

struct test {
  float a;
  long arr[3];
  long b;
  int *c;
};

int main() {
  int local1 = 4, local2 = 5, local3 = 43;
  struct test t1 = { 4.2, { 6, 3, 1 }, 4332, &local1 };
  struct test t2 = { 5435.12, { 55, 3123, 212 }, 453451, &local2 };
  t1 = t2;
  t2.a = 89.12;
  t2.arr[2] = 322;
  t2.b = 78;
  t2.c = &local3;
  printf("%f (%ld %ld %ld) %ld %d\n", t1.a, t1.arr[0], t1.arr[1], t1.arr[2], t1.b, *t1.c);
  printf("%f (%ld %ld %ld) %ld %d\n", t2.a, t2.arr[0], t2.arr[1], t2.arr[2], t2.b, *t2.c);
}
