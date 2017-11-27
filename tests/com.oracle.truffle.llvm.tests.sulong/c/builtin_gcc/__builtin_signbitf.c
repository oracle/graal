int main() {
#ifdef __clang__ // TODO: dragonegg uses incompatibe builtins!
  volatile float a = 1.;
  if (__builtin_signbitf(a)) {
    return 1;
  }
  volatile float b = -1.;
  if (!__builtin_signbitf(b)) {
    return 1;
  }
#endif
  return 0;
}
