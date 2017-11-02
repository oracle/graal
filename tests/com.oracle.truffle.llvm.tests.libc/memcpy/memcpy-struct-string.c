#include <stdio.h>
#include <string.h>

struct test {
  char *str;
};

int main() {
  struct test t1 = { "hello world!" };
  struct test t2 = { "asdf!" };
  t1 = t2;
  t2.str = "test";
  printf("%s %s\n", t1.str, t2.str);
}
