#include <stdio.h>
#include <string.h>

struct test {
  int *ptr1;
  long *ptr2[3];
};

int a = 1;
long b = 2;

int main() {
  long c = 3;
  long d = 4;
  int e = 5;
  long f = 9;
  struct test t1 = { &a, { &b, &c, &d } };
  struct test t2 = { &e, { &b, &f, &d } };
  t1 = t2;
  long g = 9;
  int h = 3;
  t2.ptr1 = &h;
  t2.ptr2[2] = &g;
  printf("%d %ld %ld %ld\n", *t1.ptr1, *t1.ptr2[0], *t1.ptr2[1], *t1.ptr2[2]);
  // printf("%d %d %d %d\n", t2.arr[0], t2.arr[1], t2.arr[2]);
}
