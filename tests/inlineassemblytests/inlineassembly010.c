int main() {
  int arg1 = 46;
  int add = 0;
  __asm__("addl $0xF, %%eax;" : "=a"(add) : "a"(arg1));
  return add;
}
