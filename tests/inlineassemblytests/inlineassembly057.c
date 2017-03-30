int main() {
  unsigned int arg = 0x1;
  unsigned int out = 0;
  __asm__("rorl $1, %%eax;" : "=a"(out) : "a"(arg));
  return (out == 0x80000000);
}
