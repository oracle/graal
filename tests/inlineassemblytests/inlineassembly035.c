int main() {
  int arg = 0xA0000000;
  int out1 = 0;
  int out2 = 0;
  __asm__("movl %2, %0; movl %2, %1;" : "=m"(out1), "=m"(out2) : "r"(arg));
  return (arg == out1) && (arg == out2);
}
