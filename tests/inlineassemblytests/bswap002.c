int main() {
  unsigned long arg = 0x123456789ABCDEF0;
  unsigned long out = 0;
  __asm__("bswapq %%rax" : "=a"(out) : "a"(arg));
  return (out == 0xF0DEBC9A78563412);
}
