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

double volatile INF = 1.0 / 0.0;
double volatile NAN = 0.0 / 0.0;

int main() {
  // ==
  assert_false(NAN == -INF);
  assert_false(-INF == NAN);
  assert_false(NAN == NAN);
  // !=
  assert_true(NAN != -INF);
  assert_true(-INF != NAN);
  assert_true(NAN != NAN);
  // <
  assert_false(NAN < -INF);
  assert_false(-INF < NAN);
  assert_false(NAN < NAN);
  // <=
  assert_false(NAN <= -INF);
  assert_false(-INF <= NAN);
  assert_false(NAN <= NAN);
  // >
  assert_false(NAN > -INF);
  assert_false(-INF > NAN);
  assert_false(NAN > NAN);
  // >=
  assert_false(NAN >= -INF);
  assert_false(-INF >= NAN);
  assert_false(NAN >= NAN);
}
