#include <stdio.h>
#include <string.h>

struct test {
  int (*func)(int);
};

int incr(int val) { return val + 1; }

int decr(int val) { return val - 1; }

int twice(int val) { return val * 2; }

int main() {
  struct test t1 = { &incr };
  struct test t2 = { &decr };
  t1 = t2;
  t2.func = &twice;
  printf("%d\n", t1.func(32));
  printf("%d\n", t2.func(342));
}
