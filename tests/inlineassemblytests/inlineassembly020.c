int main() {
  int arg1 = 40;
  int arg2 = 2;
  int mul = 0;
  __asm__("imull %%ebx, %%eax;" : "=a"(mul) : "a"(arg1), "b"(arg2));
  return mul;
}
