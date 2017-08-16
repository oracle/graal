int main() {
  unsigned short arg = 0x1234;
  unsigned short out = 0;
  __asm__("bsrw %%ax, %%cx" : "=c"(out) : "a"(arg));
  return (out == 12);
}
