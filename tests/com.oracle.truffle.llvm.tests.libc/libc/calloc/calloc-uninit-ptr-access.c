#include <stdlib.h>

struct test {
  int *ptr;
  int val;
};

int main() {
  struct test *t = calloc(1, sizeof(struct test));
  if (t->ptr != NULL) {
    abort();
  }
}
