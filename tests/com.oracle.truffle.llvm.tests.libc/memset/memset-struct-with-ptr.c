#include <assert.h>
#include <string.h>

struct test {
  char *ptr1;
  int a;
  long *ptr2;
};

int main() {
  struct test t;
  memset(&t, 0, sizeof(struct test));
  assert(t.ptr1 == NULL && t.a == 0 && t.ptr2 == NULL);
}
