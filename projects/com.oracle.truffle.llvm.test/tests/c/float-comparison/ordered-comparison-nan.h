#include <stdlib.h>
#include "../assert.h"

int main() {
  // ==
  assert_false(NAN == NUM);
  assert_false(NUM == NAN);
  assert_false(NAN == NAN);
  // !=
  assert_true(NAN != NUM);
  assert_true(NUM != NAN);
  assert_true(NAN != NAN);
  // <
  assert_false(NAN < NUM);
  assert_false(NUM < NAN);
  assert_false(NAN < NAN);
  // <=
  assert_false(NAN <= NUM);
  assert_false(NUM <= NAN);
  assert_false(NAN <= NAN);
  // >
  assert_false(NAN > NUM);
  assert_false(NUM > NAN);
  assert_false(NAN > NAN);
  // >=
  assert_false(NAN >= NUM);
  assert_false(NUM >= NAN);
  assert_false(NAN >= NAN);
}
