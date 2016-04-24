#include <stdlib.h>

int f() __attribute__((weak));

int f() { return 3; }

int main() {
  if (f() != 3) {
    abort();
  }
}
