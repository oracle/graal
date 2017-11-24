int main() {
  volatile long long a = 0x0000000000000001L;
  if(__builtin_clzll(a) != sizeof(long long)*8-1) {
    return 1;
  }
  volatile long long b = 0x000000006000beefL;
  if(__builtin_clzll(b) != sizeof(long long)*8-31) {
    return 1;
  }
  volatile long long c = 0x000000008abc0000L;
  if(__builtin_clzll(c) != sizeof(long long)*8-32) {
    return 1;
  }
  volatile long long d = 0x6000beef00000000L;
  if(__builtin_clzll(d) != sizeof(long long)*8-63) {
    return 1;
  }
  volatile long long e = 0x8abc000000000000L;
  if(__builtin_clzll(e) != sizeof(long long)*8-64) {
    return 1;
  }
  return 0;
}
