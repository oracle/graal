#include <stdlib.h>
#include <stdio.h>

static void exit1() { exit(34); }

int main() {
  atexit(exit1);
  abort();
}
