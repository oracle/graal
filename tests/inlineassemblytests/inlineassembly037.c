int main() {
  int arg1 = 0xAAAAAAAA;
  int arg2 = 0xDEADBEEF;
  int out1 = 0;
  int out2 = 0;
  int out3 = 0;
  __asm__("movl %3, %0; movl %3, %1; movl %4, %2;" : "=m"(out1), "=m"(out2), "=r"(out3) : "r"(arg1), "m"(arg2));
  return (arg1 == out1) && (arg1 == out2) && (arg2 == out3);
}
