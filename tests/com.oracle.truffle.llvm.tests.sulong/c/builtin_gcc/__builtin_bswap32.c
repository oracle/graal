int main() {
  volatile int a = 0x01234567;
  if(__builtin_bswap32(a) != 0x67452301) {
    return 1;
  }
  return 0;
}
