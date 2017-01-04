int main() {
  int arg = 0xA0000000;
  int out = 0;
  __asm__("movl %%eax, %0;" : "=r"(out) : "a"(arg));
  return arg == out;
}
