int main() {
  int arg1 = 0xAAAAAAAA;
  int arg2 = 0x55555555;
  int out = 0;
  __asm__("movl %1, %0; orl %2, %0;" : "=r"(out) : "m"(arg1), "m"(arg2));
  return (arg1 | arg2) == out;
}
