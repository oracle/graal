#include <stdlib.h>

void assert_true(int val) {
  if (!val) {
    abort();
  }
}

void assert_false(int val) {
  if (val) {
    abort();
  }
}

int main() {
  // ==
  assert_true(0.0 == 0.0);
  assert_true(0.0 == -0.0);
  assert_true(-0.0 == 0.0);
  // !=
  assert_false(0.0 != 0.0);
  assert_false(0.0 != -0.0);
  assert_false(-0.0 != 0.0);
  // <
  assert_false(0.0 < 0.0);
  assert_false(0.0 < -0.0);
  assert_false(-0.0 < 0.0);
  // <=
  assert_true(0.0 <= 0.0);
  assert_true(0.0 <= -0.0);
  assert_true(-0.0 <= 0.0);
  // >
  assert_false(0.0 > 0.0);
  assert_false(0.0 > -0.0);
  assert_false(-0.0 > 0.0);
  // >=
  assert_true(0.0 >= 0.0);
  assert_true(0.0 >= -0.0);
  assert_true(-0.0 >= 0.0);
}
