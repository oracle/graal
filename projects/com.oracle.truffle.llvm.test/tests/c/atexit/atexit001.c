#include <stdlib.h>
#include <stdio.h>

static void exit1() {
  printf("exit1!\n");
}

static void exit2() {
  printf("exit2!\n");
}


int main() {
  atexit(exit1);
  atexit(exit2);
  atexit(exit1);
  atexit(exit1);
  printf("in main\n");
  return 0;
}
