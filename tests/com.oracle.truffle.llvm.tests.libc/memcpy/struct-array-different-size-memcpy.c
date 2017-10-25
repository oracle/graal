#include <stdio.h>

struct test {
  int a;
  long b;
};

int main() {
  struct test s1[2] = { { 4, 5 }, { 1, 2 } };
  struct test s2[1] = { { 6, 2 } };
  s1[1] = s2[0];
  s2[0].a = 5;
  printf("%d %ld %d %ld\n", s1[0].a, s1[0].b, s1[1].a, s1[1].b);
  printf("%d %ld\n", s2[0].a, s2[0].b);
}
