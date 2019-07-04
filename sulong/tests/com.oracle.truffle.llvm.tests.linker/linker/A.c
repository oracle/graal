#include <stdlib.h>
#include <stdio.h>

int globalA = 1;

int methodA(int a, int b) {
  printf("A\n");
  return a + b + globalA;
}
