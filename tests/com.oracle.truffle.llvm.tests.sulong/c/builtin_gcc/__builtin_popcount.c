int main() {
  volatile int a = 0x00000000;
  if (__builtin_popcount(a) != 0) {
    return 1;
  }
  volatile int b = 0x12345678;
  if (__builtin_popcount(b) != 13) {
    return 1;
  }
  volatile int c = 0xFFFFFFFF;
  if (__builtin_popcount(c) != 32) {
    return 1;
  }
  return 0;
}
