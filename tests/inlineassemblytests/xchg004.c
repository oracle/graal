int main() {
  unsigned int arg = 0x1234;
  unsigned int out = 0;
  __asm__("xchgb %%al, %%ah" : "=a"(out) : "a"(arg));
  return (out == 0x3412);
}
