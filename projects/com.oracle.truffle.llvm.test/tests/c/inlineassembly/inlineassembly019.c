int main() {
  int arg1 = 532;
  int arg2 = 333;
  int arg3 = 76;
  int calc = 0;
  __asm__ ( "subl %%ebx, %%eax;" "subl %%ecx, %%eax;" "addl $116, %%eax;" : "=a"(calc) : "a"(arg1), "b"(arg2), "c"(arg3));
  return calc;
}
