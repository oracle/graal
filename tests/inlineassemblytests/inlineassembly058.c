int main() {
  unsigned char arg = 0x1;
  unsigned char out = 0;
  __asm__("rorb $1, %%al;" : "=a"(out) : "a"(arg));
  return (out == 0x80);
}
