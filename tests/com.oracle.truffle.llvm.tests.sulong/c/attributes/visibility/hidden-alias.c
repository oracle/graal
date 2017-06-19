#include <stdlib.h>

volatile int func() { return 4; }
volatile int func2() __attribute__((visibility("hidden"), alias("func")));

int main() {
  if (func2() != 4) {
    abort();
  }
}
