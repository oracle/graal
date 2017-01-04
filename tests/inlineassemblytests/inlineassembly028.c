int main() {
  int arg1 = 0xA0000000;
  int arg2 = 2;
  int mulLo = 0;
  int mulHi = 0;
  __asm__("mull %%ebx;" : "=a"(mulLo), "=d"(mulHi) : "a"(arg1), "b"(arg2));
  return (mulLo == 0x40000000) && (mulHi == 0x1);
}
