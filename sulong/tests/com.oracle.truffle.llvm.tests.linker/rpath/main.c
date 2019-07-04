#include <stdlib.h>
#include <stdio.h>

int foo(int a, int b);

int main() {
  int result;
  printf("Main\n");
  result = foo(5, 7);
  printf("Result: %d\n", result);
  return 0;
}
