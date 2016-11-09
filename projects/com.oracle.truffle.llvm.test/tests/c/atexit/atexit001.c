#include <stdlib.h>
#include <stdio.h>

int returnVal = 10;

static void exit1() {
  returnVal += 5;
  exit(returnVal);
}

static void exit2() {
  returnVal *= 2;
  exit(returnVal);
}

int main() {
  atexit(exit1);
  atexit(exit2);
  atexit(exit1);
  atexit(exit1);
  return 0;
}
