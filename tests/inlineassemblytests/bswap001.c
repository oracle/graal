int main() {
  unsigned int arg = 0x12345678;
  unsigned int out = 0;
  __asm__("bswapl %%eax" : "=a"(out) : "a"(arg));
  return (out == 0x78563412);
}
