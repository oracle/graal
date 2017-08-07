int main() {
  unsigned long arg1 = 0xFFFFFFFFFFFFFFFFL;
  unsigned long arg2 = 0xFFFFFFFFFFFFFFFFL;
  unsigned long mulLo = 0;
  unsigned long mulHi = 0;
  __asm__("mulq %%rbx;" : "=a"(mulLo), "=d"(mulHi) : "a"(arg1), "b"(arg2));
  return (mulLo == 1) && (mulHi == 0xFFFFFFFFFFFFFFFEL);
}
