int main() {
  volatile int a = 0x00000001;
  if (__builtin_clz(a) != sizeof(int) * 8 - 1) {
    return 1;
  }
  volatile int b = 0x6000beef;
  if (__builtin_clz(b) != sizeof(int) * 8 - 31) {
    return 1;
  }
  volatile int c = 0x8abc0000;
  if (__builtin_clz(c) != sizeof(int) * 8 - 32) {
    return 1;
  }
  return 0;
}
