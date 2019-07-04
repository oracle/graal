#include <stdlib.h>
#include <stdio.h>

int bar(int a, int b);

int foo(int a, int b) {
  printf("fooA\n");
  return (b - a) + bar(3, 9);
}
