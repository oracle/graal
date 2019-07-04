#include <stdlib.h>
#include <stdio.h>

__attribute__((visibility("hidden")))
int globalD = 11;

int globalH = 12;

__attribute__((visibility("hidden")))
int methodD(int a, int b) {
  printf("HiddenD used by H\n");
  return a - b;
}

int methodH(int a, int b) {
  printf("H\n");
  return methodD(a, b) + (a << b) + globalD + globalH;
}
