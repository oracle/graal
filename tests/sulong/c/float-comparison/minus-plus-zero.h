#include <stdlib.h>
#include "../assert.h"

int main() {
  // ==
  assert_true(ZERO == ZERO);
  assert_true(ZERO == MINUS_ZERO);
  assert_true(MINUS_ZERO == ZERO);
  // !=
  assert_false(ZERO != ZERO);
  assert_false(ZERO != MINUS_ZERO);
  assert_false(MINUS_ZERO != ZERO);
  // <
  assert_false(ZERO < ZERO);
  assert_false(ZERO < MINUS_ZERO);
  assert_false(MINUS_ZERO < ZERO);
  // <=
  assert_true(ZERO <= ZERO);
  assert_true(ZERO <= MINUS_ZERO);
  assert_true(MINUS_ZERO <= ZERO);
  // >
  assert_false(ZERO > ZERO);
  assert_false(ZERO > MINUS_ZERO);
  assert_false(MINUS_ZERO > ZERO);
  // >=
  assert_true(ZERO >= ZERO);
  assert_true(ZERO >= MINUS_ZERO);
  assert_true(MINUS_ZERO >= ZERO);
}
