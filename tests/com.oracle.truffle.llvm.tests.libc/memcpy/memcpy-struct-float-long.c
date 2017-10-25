#include <stdio.h>

struct test {
  float a;
  long b;
  float c;
};

int main() {
  struct test s1 = { 2.3, 8, -5.4 };
  struct test s2 = { 54.32, 45, 89032.03 };
  s1 = s2;
  s2.a = 0;
  s2.b = 3;
  s1.c = 23;
  printf("%f %ld %f\n", s1.a, s1.b, s1.c);
  printf("%f %ld %f\n", s2.a, s2.b, s2.c);
}
