#include <stdlib.h>
#include <stdio.h>

int globalC = 9;
int globalG = 10;

int methodC(int a, int b) {
  printf("NonFileLocalC used by G\n");
  return a - b;
}

int methodG(int a, int b) {
  printf("G\n");
  return methodC(a, b) + (a << b) + globalC + globalG;
}
