#include <stdlib.h>
#include <stdio.h>

int methodB(int a, int b);
int methodC(int a, int b);
int methodE(int a, int b);

extern int globalB;
extern int globalC;
extern int globalE;

int globalD = 4;

int methodD(int a, int b) {
  printf("D\n");
  return methodB(a, b) + methodC(a, b) + methodE(a, 2) + globalD + globalB + globalC + globalE;
}
