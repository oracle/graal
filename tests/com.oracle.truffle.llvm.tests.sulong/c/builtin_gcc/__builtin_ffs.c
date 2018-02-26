int main() {
  volatile int a = 0x00000001;
  if (__builtin_ffs(a) != 1) {
    return 1;
  }
  volatile int b = 0x6000beef;
  if (__builtin_ffs(b) != 1) {
    return 1;
  }
  volatile int c = 0x8abc0000;
  if (__builtin_ffs(c) != 19) {
    return 1;
  }
  volatile int d = 0x00000000;
  if (__builtin_ffs(d) != 0) {
    return 1;
  }
  return 0;
}
