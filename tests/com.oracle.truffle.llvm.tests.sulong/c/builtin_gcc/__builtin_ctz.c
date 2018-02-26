int main() {
  volatile int a = 0x00000001;
  if (__builtin_ctz(a) != 0) {
    return 1;
  }
  volatile int b = 0x00000002;
  if (__builtin_ctz(b) != 1) {
    return 1;
  }
  volatile int c = 0x80000000;
  if (__builtin_ctz(c) != 31) {
    return 1;
  }
  return 0;
}
