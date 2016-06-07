int main() {
  int arg1 = 5;
  int arg2 = 9;
  int or = 0;
  __asm__("orl %%ebx, %%eax;" : "=a"(or) : "a"(arg1), "b"(arg2));
  return or;
}
