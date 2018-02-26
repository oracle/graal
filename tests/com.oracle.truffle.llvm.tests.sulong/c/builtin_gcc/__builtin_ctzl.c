int main() {
  volatile long a = 0x0000000000000001L;
  if (__builtin_ctzl(a) != 0) {
    return 1;
  }
  volatile long b = 0x0000000000000002;
  if (__builtin_ctzl(b) != 1) {
    return 1;
  }
  volatile long c = 0x0000000080000000;
  if (__builtin_ctzl(c) != 31) {
    return 1;
  }
  volatile long d = 0x0000000100000000;
  if (__builtin_ctzl(d) != 32) {
    return 1;
  }
  volatile long e = 0x8000000000000000;
  if (__builtin_ctzl(e) != 63) {
    return 1;
  }
  return 0;
}
