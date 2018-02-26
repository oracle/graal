int main() {
  volatile float fPos = 1.f;
  volatile float fNeg = -2.f;
  if (__builtin_copysign(fPos, fNeg) != -1.f) {
    return 1;
  }
  if (__builtin_copysign(fNeg, fPos) != 2.f) {
    return 1;
  }
  volatile double dPos = 1.;
  volatile double dNeg = -2.;
  if (__builtin_copysign(dPos, dNeg) != -1.) {
    return 1;
  }
  if (__builtin_copysign(dNeg, dPos) != 2.) {
    return 1;
  }
  volatile long double lPos = 1.;
  volatile long double lNeg = -2.;
  if (__builtin_copysign(lPos, lNeg) != -1.) {
    return 1;
  }
  if (__builtin_copysign(lNeg, lPos) != 2.) {
    return 1;
  }
  return 0;
}
