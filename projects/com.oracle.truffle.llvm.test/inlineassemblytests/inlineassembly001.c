int main() {
  int arg1 = 40;
  int arg2 = 2;
  int add = 0;
  __asm__("addl %%ebx, %%eax;" : "=a"(add) : "a"(arg1), "b"(arg2));
  return add;
}
