int main() {
  unsigned int arg1 = 0x12345678;
  unsigned int arg2 = 0x87654321;
  unsigned int out1 = 0;
  unsigned int out2 = 0;
  __asm__("xchgl %%eax, %%ecx" : "=a"(out1), "=c"(out2) : "a"(arg1), "c"(arg2));
  return (out1 == 0x87654321) && (out2 == 0x12345678);
}
