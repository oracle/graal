int main() {
  volatile long long a = 0x0000000000000001L;
  if(__builtin_ctzll(a) != 0) {
    return 1;
  }
  volatile long long b = 0x0000000000000002;
  if(__builtin_ctzll(b) != 1) {
    return 1;
  }
  volatile long long c = 0x0000000080000000;
  if(__builtin_ctzll(c) != 31) {
    return 1;
  }
  volatile long long d = 0x0000000100000000;
  if(__builtin_ctzll(d) != 32) {
    return 1;
  }
  volatile long long e = 0x8000000000000000;
  if(__builtin_ctzll(e) != 63) {
    return 1;
  }
  return 0;
}
