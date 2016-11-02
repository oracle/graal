#include <stdlib.h>
#include <stdio.h>

static void exit1() { printf("something went wrong!\n"); }

int main() {
  atexit(exit1);
  abort();
}
