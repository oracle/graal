#include <stdio.h>

struct test {
  int a;
  long b;
};

int main() {
  struct test s1 = { 4, 5 };
  struct test s2 = { 7, 8 };
  s1 = s2;
  s2.a = 9;
  s2.b = 3;
  printf("%d %ld\n", s1.a, s1.b);
  printf("%d %ld\n", s2.a, s2.b);
}
