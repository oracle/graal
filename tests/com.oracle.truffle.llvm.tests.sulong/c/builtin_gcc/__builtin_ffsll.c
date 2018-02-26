int main() {
  volatile long long a = 0x0000000000000001L;
  if (__builtin_ffsll(a) != 1) {
    return 1;
  }
  volatile long long b = 0x000000006000beefL;
  if (__builtin_ffsll(b) != 1) {
    return 1;
  }
  volatile long long c = 0x000000008abc0000L;
  if (__builtin_ffsll(c) != 19) {
    return 1;
  }
  volatile long long d = 0x0000010000000000L;
  if (__builtin_ffsll(d) != 41) {
    return 1;
  }
  volatile long long e = 0x0000000000000000L;
  if (__builtin_ffsll(e) != 0) {
    return 1;
  }
  return 0;
}
