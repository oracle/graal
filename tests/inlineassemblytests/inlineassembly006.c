int main() {
  int arg1 = 5;
  int arg2 = 9;
  int xor = 0;
  __asm__("xorl %%ebx, %%eax;" : "=a"(xor) : "a"(arg1), "b"(arg2));
  return xor;
}
