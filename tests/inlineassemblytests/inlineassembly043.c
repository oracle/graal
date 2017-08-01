int main() {
  int arg1 = 0xAAAAAAAA;
  int arg2 = 0xFFFFFFFF;
  int arg3 = 0x00000000;
  int out = 0;
  char flags = 0;
  __asm__("addl %2, %2; movl %3, %%edx; adcl %4, %%edx; lahf; movb %%ah, %1; movl %%edx, %0;" : "=r"(out), "=a"(flags) : "a"(arg1), "b"(arg2), "c"(arg3) : "edx");
  return out == 0;
}
