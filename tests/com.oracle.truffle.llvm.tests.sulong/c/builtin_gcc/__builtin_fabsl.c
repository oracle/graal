int main() {
#ifdef __clang__ // TODO: dragonegg uses native calls which do not work with X86_FP80
  volatile long double a = 0.125;
  if (__builtin_fabsl(a) != 0.125) {
    return 1;
  }
  volatile long double b = -0.125;
  if (__builtin_fabsl(b) != 0.125) {
    return 1;
  }
  volatile long double c = 0.;
  if (__builtin_fabsl(c) != 0.) {
    return 1;
  }
  volatile long double d = -0.;
  if (__builtin_fabsl(d) != 0.) {
    return 1;
  }
#endif
  return 0;
}
