#include <limits.h>
#include <stdio.h>
#include <stdlib.h>

int main() {
  volatile unsigned short s = USHRT_MAX;
  volatile float f = s;
  if (f != USHRT_MAX) {
    abort();
  }
}
