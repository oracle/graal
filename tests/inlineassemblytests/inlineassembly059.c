int main() {
  unsigned char arg = 0x55;
  unsigned char out = 0;
  __asm__("movb $3, %%cl; rorb %%cl, %%al;" : "=a"(out) : "a"(arg) : "%cl");
  return (out == 0xAA);
}
