int main() {
  volatile long a = 0x0000000000000001L;
  if(__builtin_ffsl(a) != 1) {
    return 1;
  }
  volatile long b = 0x000000006000beefL;
  if(__builtin_ffsl(b) != 1) {
    return 1;
  }
  volatile long c = 0x000000008abc0000L;
  if(__builtin_ffsl(c) != 19) {
    return 1;
  }
  volatile long d = 0x0000010000000000L;
  if(__builtin_ffsl(d) != 41) {
    return 1;
  }
  volatile long e = 0x0000000000000000L;
  if(__builtin_ffsl(e) != 0) {
    return 1;
  }
  return 0;
}
