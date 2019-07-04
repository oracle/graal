#include <stdlib.h>
#include <stdio.h>

static int globalA = 8;

static int methodA(int a, int b) {
  printf("FileLocalA\n");
  return a * b + globalA;
}
