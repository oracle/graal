int main() {
  int arg1 = 33;
  int sal = 0;
  __asm__("sall $4, %%eax;" : "=a"(sal) : "a"(arg1));
  return sal;
}
