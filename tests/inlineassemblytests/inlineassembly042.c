int main() {
  int arg1 = 0xAAAAAAAA;
  int arg2 = 0x02;
  int arg3 = 0x02;
  int out = 0;
  __asm__("addl %1, %1; adcl %2, %3; movl %3, %0;" : "=r"(out) : "a"(arg1), "b"(arg2), "c"(arg3));
  return out == 5;
}
