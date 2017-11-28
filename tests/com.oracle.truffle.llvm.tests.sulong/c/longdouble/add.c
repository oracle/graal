#include <math.h>
#include "longdouble.h"

int main(void) {
  TEST(0, 0, +, 0);
  TEST(0, -1, +, 1);
  TEST(1, -4, +, 5);
  TEST(0, -4, +, 4);
  TEST(-1, -6, +, 5);
  TEST(1, 5, +, -4);
  TEST(0, 5, +, -5);
  TEST(-1, 5, +, -6);
  TEST(13, 4, +, 9);
  TEST(INFINITY, INFINITY, +, INFINITY);
}
