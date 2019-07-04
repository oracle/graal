#include <stdlib.h>
#include <stdio.h>

int methodC(int a, int b);
int methodD(int a, int b);
int methodF(int a, int b);
int methodG(int a, int b);
int methodH(int a, int b);

extern int globalC;
extern int globalD;
extern int globalF;
extern int globalG;
extern int globalH;

int main() {
  int result;
  printf("Main\n");
  result = methodC(5, 7) + methodD(3, 9) + methodF(6, 10) + methodG(1, 2) + methodH(5, 2) + globalC + globalD + globalF + globalG + globalH;
  printf("Result: %d\n", result);
  return 0;
}
