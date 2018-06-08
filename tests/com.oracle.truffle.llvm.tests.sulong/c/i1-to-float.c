#include <assert.h>

float __attribute__((noinline)) test(float val) {
  return val > 0 ? 1 : 0;
}

int main() {
  assert(test(1.0) == 1);
  assert(test(2.0) == 1);
  assert(test(-1.0) == 0);
  assert(test(0) == 0);
}
