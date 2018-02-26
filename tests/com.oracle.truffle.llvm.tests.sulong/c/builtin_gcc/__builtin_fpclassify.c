int main() {
  volatile float fNan = __builtin_nanf("");
  if (__builtin_fpclassify(11, 12, 13, 14, 15, fNan) != 11) {
    return 1;
  }
  volatile float fInf = __builtin_inff();
  if (__builtin_fpclassify(11, 12, 13, 14, 15, fInf) != 12) {
    return 1;
  }
  volatile float fOne = 1.f;
  if (__builtin_fpclassify(11, 12, 13, 14, 15, fOne) != 13) {
    return 1;
  }
  volatile float fZero = 0.f;
  if (__builtin_fpclassify(11, 12, 13, 14, 15, fZero) != 15) {
    return 1;
  }
  volatile double dNan = __builtin_nan("");
  if (__builtin_fpclassify(11, 12, 13, 14, 15, dNan) != 11) {
    return 1;
  }
  volatile double dInf = __builtin_inf();
  if (__builtin_fpclassify(11, 12, 13, 14, 15, dInf) != 12) {
    return 1;
  }
  volatile double dOne = 1.;
  if (__builtin_fpclassify(11, 12, 13, 14, 15, dOne) != 13) {
    return 1;
  }
  volatile double dZero = 0.;
  if (__builtin_fpclassify(11, 12, 13, 14, 15, dZero) != 15) {
    return 1;
  }
#ifdef __clang__ // TODO: dragonegg uses native calls which do not work with X86_FP80
  volatile long double lNan = __builtin_nanl("");
  if (__builtin_fpclassify(11, 12, 13, 14, 15, lNan) != 11) {
    return 1;
  }
  volatile long double lInf = __builtin_infl();
  if (__builtin_fpclassify(11, 12, 13, 14, 15, lInf) != 12) {
    return 1;
  }
  volatile long double lOne = 1.;
  if (__builtin_fpclassify(11, 12, 13, 14, 15, lOne) != 13) {
    return 1;
  }
  volatile long double lZero = 0.;
  if (__builtin_fpclassify(11, 12, 13, 14, 15, lZero) != 15) {
    return 1;
  }
#endif
  return 0;
}
