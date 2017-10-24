#include <stdio.h>
#include <string.h>

struct test {
  char a;
  int b;
  long c;
  char d;
  char e[3];
};

int main() {
  struct test t;
  memset(&t, 0, sizeof t);
  printf("%d %d %ld %d %d %d %d\n", t.a, t.b, t.c, t.d, t.e[0], t.e[1], t.e[2]);
}
