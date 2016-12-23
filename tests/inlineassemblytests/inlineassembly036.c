int main() {
  int arg = 0xA0000000;
  int out1 = 0;
  int out2 = 0;
  int out3 = 0;
  __asm__("movl %3, %0; movl %3, %1; movl $0xDEADBEEF, %2;" : "=m"(out1), "=m"(out2), "=r"(out3) : "r"(arg));
  return (arg == out1) && (arg == out2) && (out3 == 0xDEADBEEF);
}
