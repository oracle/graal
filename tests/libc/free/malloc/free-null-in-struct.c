#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

struct test {
  int a;
  int *ptr;
};

int main() {
  struct test t;
  t.a = 5;
  t.ptr = NULL;
  free(t.ptr);
}
