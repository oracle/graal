#include <stdlib.h>
#include <stdio.h>

int globalB = 2;

int methodB(int a, int b) {
  printf("B\n");
  return a - b - globalB;
}
