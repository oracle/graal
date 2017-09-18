int main() {
  unsigned char arg1 = 0x12;
  unsigned char arg2 = 0x34;
  unsigned char out1 = 0;
  unsigned char out2 = 0;
  __asm__("xaddb %%al, %%cl" : "=a"(out1), "=c"(out2) : "a"(arg1), "c"(arg2));
  return (out1 == 0x34) && (out2 == 0x46);
}
