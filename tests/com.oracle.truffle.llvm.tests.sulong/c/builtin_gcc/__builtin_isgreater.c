int main() {
  volatile float pos1 = 1.;
  volatile float neg1 = -1.;
  volatile float posZero = 0.;
  volatile float negZero = -0.;
  if (__builtin_isgreater(neg1, neg1) != 0) {
    return 1;
  }
  if (__builtin_isgreater(posZero, negZero) != 0) {
    return 1;
  }
  if (__builtin_isgreater(pos1, pos1) != 0) {
    return 1;
  }
  if (__builtin_isgreater(neg1, pos1) != 0) {
    return 1;
  }
  if (__builtin_isgreater(pos1, neg1) == 0) {
    return 1;
  }
  return 0;
}
