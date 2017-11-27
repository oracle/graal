int main() {
#ifdef __clang__ // TODO: dragonegg uses incompatibe builtins!
  volatile long double a = 1.;
  if (__builtin_signbitl(a)) {
    return 1;
  }
  volatile long double b = -1.;
  if (!__builtin_signbitl(b)) {
    return 1;
  }
#endif
  return 0;
}
