int main() {
  volatile long a = 0x0000000000000000;
  if(__builtin_popcountl(a) != 0) {
    return 1;
  }
  volatile long b = 0x0000000012345678;
  if(__builtin_popcountl(b) != 13) {
    return 1;
  }
  volatile long c = 0x00000000FFFFFFFF;
  if(__builtin_popcountl(c) != 32) {
    return 1;
  }
  volatile long d = 0x0123456789ABCDEF;
  if(__builtin_popcountl(d) != 32) {
    return 1;
  }
  volatile long e = 0xFFFFFFFFFFFFFFFF;
  if(__builtin_popcountl(e) != 64) {
    return 1;
  }
  return 0;
}
