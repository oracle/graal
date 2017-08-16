int main() {
  unsigned int arg = 0x12345678;
  unsigned int out = 0;
  __asm__("bsrl %%eax, %%ecx" : "=c"(out) : "a"(arg));
  return (out == 28);
}
