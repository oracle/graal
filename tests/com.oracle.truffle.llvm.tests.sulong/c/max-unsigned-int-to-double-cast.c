#include <limits.h>
#include <stdio.h>
#include <stdlib.h>

void testCase(double value, unsigned int expected) {
  unsigned int result = (unsigned int)value;
  if (result != expected) {
    printf("%d\n", result);
    abort();
  }
}

int main() {
  testCase(UINT_MAX, -1);
  testCase(UINT_MAX - 0.03, -2);
  testCase(UINT_MAX - 1, -2);
  testCase(UINT_MAX - 2, -3);
  testCase(UINT_MAX - 5, -6);

  testCase(UINT_MAX - 2.5, -4);
  testCase(UINT_MAX - 2.4, -4);
  testCase(UINT_MAX - 2.6, -4);

  testCase(UINT_MAX / 2, 2147483647);
  testCase(UINT_MAX / 2 + 1, -2147483648);
  testCase(UINT_MAX / 2 - 1.0, 2147483646);
  testCase(UINT_MAX / 2 + 1.999999999, -2147483647);

  testCase(-1, -1);
  testCase(0, 0);
  testCase(1.5, 1);
  testCase(INT_MAX + 1, -2147483648);
  testCase(INT_MAX, 2147483647);
}
