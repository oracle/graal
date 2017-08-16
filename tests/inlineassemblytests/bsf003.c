int main() {
  unsigned long arg = 0x0123456789ABCD00;
  unsigned long out = 0;
  __asm__("bsfq %%rax, %%rcx" : "=c"(out) : "a"(arg));
  return (out == 8);
}
