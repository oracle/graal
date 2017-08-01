int main() {
  unsigned char arg = 0x01;
  unsigned char out = 0;
  __asm__("rolb $1, %%al;" : "=a"(out) : "a"(arg));
  return (out == 0x02);
}
