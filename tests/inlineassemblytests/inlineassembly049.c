int main() {
  unsigned long arg1 = 0x1000000000000000L;
  unsigned long arg2 = 0x1000000000000000L;
  unsigned long mulLo = 0;
  unsigned long mulHi = 0;
  __asm__("mulq %%rbx;" : "=a"(mulLo), "=d"(mulHi) : "a"(arg1), "b"(arg2));
  return (mulLo == 0) && (mulHi == 0x100000000000000L);
}
