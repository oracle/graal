int main() {
  int arg1 = 40;
  int add = 0;
  __asm__("addl $15, %%eax;" : "=a"(add) : "a"(arg1));
  return add;
}
