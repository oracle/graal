#ifdef REF_COMPILER
#include <stdlib.h>
#endif

struct test {
  int a;
  int b;
  int c;
};

int main() {
  struct test *t1 = (struct test *)malloc(sizeof(struct test));
  struct test *t2 = (struct test *)malloc(sizeof(struct test));
  t1->a = 1;
  t1->b = 2;
  t1->c = 3;
  t2->a = 4;
  t2->b = 5;
  t2->c = 6;
  return t1->a + t1->b + t1->c + t2->a + t2->b + t2->c;
}
