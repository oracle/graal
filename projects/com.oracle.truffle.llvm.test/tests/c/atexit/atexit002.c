#include <stdlib.h>
#include <stdio.h>

static void exit1() { exit(42); }

int main() {
  atexit(exit1);
  return 0;
}
