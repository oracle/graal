int main() {
  unsigned short arg = 0x1234;
  unsigned short out = 0;
  __asm__("bsfw %%ax, %%cx" : "=c"(out) : "a"(arg));
  return (out == 2);
}
