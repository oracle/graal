int main() {
  volatile float a = __builtin_inff();
  if(!__builtin_isinf(a)) {
    return 1;
  }
  volatile double b = __builtin_inf();
  if(!__builtin_isinf(b)) {
    return 1;
  }
#ifdef __clang__ // TODO: dragonegg uses native calls which do not work with X86_FP80
  volatile long double c = __builtin_infl();
  if(!__builtin_isinf(c)) {
    return 1;
  }
#endif
  volatile float d = __builtin_nanf("");
  if(__builtin_isinf(d)) {
    return 1;
  }
  volatile double e = __builtin_nan("");
  if(__builtin_isinf(e)) {
    return 1;
  }
#ifdef __clang__ // TODO: dragonegg uses native calls which do not work with X86_FP80
  volatile long double f = __builtin_nanl("");
  if(__builtin_isinf(f)) {
    return 1;
  }
#endif
  return 0;
}
