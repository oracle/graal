#include <stdio.h>

struct test {
  int a;
  long b;
};

int main() {
  struct test s1[1] = { { 4, 5 } };
  struct test s2[2] = { { 1, 3 }, { 7, 8 } };
  s1[0] = s2[1];

  printf("%d %ld\n", s1[0].a, s1[0].b);
  printf("%d %ld %d %ld\n", s2[0].a, s2[0].b, s2[1].a, s2[1].b);
}
