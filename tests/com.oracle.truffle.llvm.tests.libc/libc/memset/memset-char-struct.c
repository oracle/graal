#include <stdio.h>
#include <string.h>

struct test {
  char a;
  char b[3];
  char c;
  char d[2];
  char e;
};

int main() {
  struct test t;
  memset(&t, 'a', sizeof t);
  printf("%c %c %c %c %c %c %c %c\n", t.a, t.b[0], t.b[1], t.b[2], t.c, t.d[0], t.d[1], t.e);
}
