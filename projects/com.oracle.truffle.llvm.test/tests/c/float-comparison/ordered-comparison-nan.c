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

double volatile NUM = 3;
double volatile NAN = 0.0 / 0.0;

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
