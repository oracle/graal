int main() {
  unsigned long arg = 0x0123456789ABCDEF;
  unsigned long out = 0;
  __asm__("bsrq %%rax, %%rcx" : "=c"(out) : "a"(arg));
  return (out == 56);
}
