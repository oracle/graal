int main() {
  volatile float fNan = __builtin_nanf("");
  if (__builtin_isnormal(fNan)) {
    return 1;
  }
  volatile float fInf = __builtin_inff();
  if (__builtin_isnormal(fInf)) {
    return 1;
  }
  volatile float fZero = 0.f;
  if (__builtin_isnormal(fZero)) {
    return 1;
  }
  volatile float fOne = 1.f;
  if (!__builtin_isnormal(fOne)) {
    return 1;
  }
  volatile double dNan = __builtin_nan("");
  if (__builtin_isnormal(dNan)) {
    return 1;
  }
  volatile double dInf = __builtin_inf();
  if (__builtin_isnormal(dInf)) {
    return 1;
  }
  volatile double dZero = 0.;
  if (__builtin_isnormal(dZero)) {
    return 1;
  }
  volatile double dOne = 1.;
  if (!__builtin_isnormal(dOne)) {
    return 1;
  }
  volatile double lNan = __builtin_nanl("");
  if (__builtin_isnormal(lNan)) {
    return 1;
  }
  volatile double lInf = __builtin_infl();
  if (__builtin_isnormal(lInf)) {
    return 1;
  }
  volatile double lZero = 0.;
  if (__builtin_isnormal(lZero)) {
    return 1;
  }
  volatile double lOne = 1.;
  if (!__builtin_isnormal(lOne)) {
    return 1;
  }
  return 0;
}
