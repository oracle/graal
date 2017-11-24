int main() {
  volatile short a = 0x0123;
#ifdef __clang__ // TODO: dragonegg uses incompatibe builtins!
  if(__builtin_bswap16(a) != 0x2301) {
    return 1;
  }
#endif
  return 0;
}
