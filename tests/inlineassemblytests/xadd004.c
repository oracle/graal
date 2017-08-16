int main() {
  unsigned long arg1 = 0x123456789ABCDEF0;
  unsigned long arg2 = 0xFEDCBA9876543210;
  unsigned long out1 = 0;
  unsigned long out2 = 0;
  __asm__("xaddq %%rax, %%rcx" : "=a"(out1), "=c"(out2) : "a"(arg1), "c"(arg2));
  return (out1 == 0xFEDCBA9876543210) && (out2 == 0x1111111111111100);
}
