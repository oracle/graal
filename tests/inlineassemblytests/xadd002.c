int main() {
  unsigned short arg1 = 0x1234;
  unsigned short arg2 = 0x5678;
  unsigned short out1 = 0;
  unsigned short out2 = 0;
  __asm__("xaddw %%ax, %%cx" : "=a"(out1), "=c"(out2) : "a"(arg1), "c"(arg2));
  return (out1 == 0x5678) && (out2 == 0x68AC);
}
