int main() {
  int arg = 0xA0000000;
  int out = 0;
  __asm__("movl %1, %0;" : "=r"(out) : "m"(arg));
  return arg == out;
}
