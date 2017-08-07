int main() {
  unsigned int arg = 0x80000000;
  unsigned int out = 0;
  __asm__("roll $1, %%eax;" : "=a"(out) : "a"(arg));
  return (out == 0x1);
}
