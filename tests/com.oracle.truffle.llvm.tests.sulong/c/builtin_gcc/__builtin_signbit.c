int main() {
#ifdef __clang__ // TODO: dragonegg uses incompatibe builtins!
  volatile float a = 1.;
  if(__builtin_signbit(a)) {
    return 1;
  }
  volatile float b = -1.;
  if(!__builtin_signbit(b)) {
    return 1;
  }
  volatile double c = 1.;
  if(__builtin_signbit(c)) {
    return 1;
  }
  volatile double d = -1.;
  if(!__builtin_signbit(d)) {
    return 1;
  }
  volatile long double e = 1.;
  if(__builtin_signbit(e)) {
    return 1;
  }
  volatile long double f = -1.;
  if(!__builtin_signbit(f)) {
    return 1;
  }
#endif
  return 0;
}
