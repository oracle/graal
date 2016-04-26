#include <stdlib.h>

int foo(int *a) { return *a + *a; }

int boo(int *a) { return foo(a); }

int main() {
  int *a = (int *)malloc(8);
  int *b = (int *)malloc(8);
  *a = 5;
  *b = 4;

  return boo(a) + boo(a) + boo(b) + boo(b) + boo(a) + boo(b);
}
