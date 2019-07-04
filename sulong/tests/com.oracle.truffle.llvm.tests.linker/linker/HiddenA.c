#include <stdlib.h>
#include <stdio.h>

__attribute__((visibility("hidden")))
int globalA = 13;

__attribute__((visibility("hidden")))
int methodA(int a, int b) {
  printf("HiddenA\n");
  return a * b + globalA;
}
