#include <stdlib.h>
#include <stdio.h>

static int globalA = 6;

int globalF = 7;

static int methodA(int a, int b) {
  printf("FileLocalA used by F\n");
  return a - b;
}

int methodF(int a, int b) {
  printf("F\n");
  return methodA(a, b) + (a << b) + globalA + globalF;
}
