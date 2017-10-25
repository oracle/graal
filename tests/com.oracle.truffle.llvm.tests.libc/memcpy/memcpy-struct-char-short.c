#include <stdio.h>

struct test {
  char a;
  short b;
};

int main() {
  struct test s1 = { 'w', 32432 };
  struct test s2 = { 's', 3231 };
  s1 = s2;
  s2.a = 'a';
  s2.b = 2132;
  printf("%c %d\n", s1.a, s1.b);
  printf("%c %d\n", s2.a, s2.b);
}
