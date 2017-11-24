int main() {
  volatile float a = __builtin_nanf("");
  if (__builtin_isfinite(a)) {
    return 1;
  }
  volatile float b = __builtin_inff();
  if (__builtin_isfinite(b)) {
    return 1;
  }
  volatile double c = __builtin_nan("");
  if (__builtin_isfinite(c)) {
    return 1;
  }
  volatile double d = __builtin_inf();
  if (__builtin_isfinite(d)) {
    return 1;
  }
#ifdef __clang__ // TODO: dragonegg uses native calls which do not work with X86_FP80
  volatile long double e = __builtin_nanl("");
  if (__builtin_isfinite(e)) {
    return 1;
  }
  volatile long double f = __builtin_infl();
  if (__builtin_isfinite(f)) {
    return 1;
  }
#endif
  volatile float g = 0;
  if (!__builtin_isfinite(g)) {
    return 1;
  }
  volatile double h = 0;
  if (!__builtin_isfinite(h)) {
    return 1;
  }
#ifdef __clang__ // TODO: dragonegg uses native calls which do not work with X86_FP80
  volatile long double i = 0;
  if (!__builtin_isfinite(i)) {
    return 1;
  }
#endif
  return 0;
}
