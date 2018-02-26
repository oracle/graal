int main() {
  volatile long long a = 0x0000000000000000;
  if (__builtin_popcountll(a) != 0) {
    return 1;
  }
  volatile long long b = 0x0000000012345678;
  if (__builtin_popcountll(b) != 13) {
    return 1;
  }
  volatile long long c = 0x00000000FFFFFFFF;
  if (__builtin_popcountll(c) != 32) {
    return 1;
  }
  volatile long long d = 0x0123456789ABCDEF;
  if (__builtin_popcountll(d) != 32) {
    return 1;
  }
  volatile long long e = 0xFFFFFFFFFFFFFFFF;
  if (__builtin_popcountll(e) != 64) {
    return 1;
  }
  return 0;
}
