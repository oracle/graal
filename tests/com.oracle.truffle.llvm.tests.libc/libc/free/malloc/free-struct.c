#include <stdlib.h>

struct test {
  double a;
  char b;
};

int main() {
  struct test *s = malloc(sizeof(struct test));
  s->a = 5.4;
  s->b = 7;
  int sum = s->a + s->b;
  free(s);
  return sum;
}
