#include <stdio.h>

struct test {
  int a;
  long b;
};

int main() {
  struct test s1[2] = { { 4, 5 }, { 7, 8 } };
  struct test s2[2] = { { 6, 2 }, { 1, 3 } };
  s1[0] = s2[1];
  s2[1].a = 9;
  s2[1].b = 3;
  printf("%d %ld %d %ld\n", s1[0].a, s1[0].b, s1[1].a, s1[1].b);
  printf("%d %ld %d %ld\n", s2[0].a, s2[0].b, s2[1].a, s2[1].b);
}
