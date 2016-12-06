int main() {
  int arg1 = 0x70000000;
  int arg2 = 7;
  int mulLo = 0;
  int mulHi = 0;
  __asm__("imull %%ebx;" : "=a"(mulLo), "=d"(mulHi) : "a"(arg1), "b"(arg2));
  return (mulLo == 0x10000000) && (mulHi == 0x3);
}
