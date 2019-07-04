#include <stdlib.h>
#include <stdio.h>

int globalE = 5;

int methodE(int a, int b) {
  printf("E\n");
  return (a << b) + globalE;
}
