int main() {
  unsigned char arg1 = 0x55;
  unsigned char arg2 = 0xAA;
  unsigned char out1 = 0;
  unsigned char out2 = 0;
  __asm__("xchgb %%al, %%cl" : "=a"(out1), "=c"(out2) : "a"(arg1), "c"(arg2));
  return (out1 == 0xAA) && (out2 == 0x55);
}
