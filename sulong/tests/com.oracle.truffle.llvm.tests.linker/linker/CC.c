#include <stdlib.h>
#include <stdio.h>

int methodA(int a, int b);
int methodB(int a, int b);

extern int globalA;
extern int globalB;

int globalC = 3;

int methodC(int a, int b) {
  printf("C\n");
  return methodA(a, b) + methodB(a, b) + globalA + globalB + globalC;
}
