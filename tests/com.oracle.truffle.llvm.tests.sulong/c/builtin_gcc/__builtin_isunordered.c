int main() {
  volatile float pos1 = 1.;
  volatile float neg1 = -1.;
  volatile float posZero = 0.;
  volatile float negZero = -0.;
  volatile float nan = __builtin_nanf("");
  if (__builtin_isunordered(neg1, neg1) != 0) {
    return 1;
  }
  if (__builtin_isunordered(posZero, negZero) != 0) {
    return 1;
  }
  if (__builtin_isunordered(pos1, nan) == 0) {
    return 1;
  }
  if (__builtin_isunordered(nan, pos1) == 0) {
    return 1;
  }
  if (__builtin_isunordered(nan, nan) == 0) {
    return 1;
  }
  return 0;
}
