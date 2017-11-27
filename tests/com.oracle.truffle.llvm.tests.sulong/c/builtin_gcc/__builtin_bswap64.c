int main() {
  volatile long a = 0x0123456789ABCDEFL;
  if (__builtin_bswap64(a) != 0xEFCDAB8967452301L) {
    return 1;
  }
  return 0;
}
