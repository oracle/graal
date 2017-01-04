int main() {
  int arg1 = 0x12345;
  int arg2 = 0x74;
  int quot = 0;
  int rem = 0;
  __asm__("xorl %%edx, %%edx; idivl %%ebx;" : "=a"(quot), "=d"(rem) : "a"(arg1), "b"(arg2));
  return (quot * arg2 + rem) == arg1;
}
