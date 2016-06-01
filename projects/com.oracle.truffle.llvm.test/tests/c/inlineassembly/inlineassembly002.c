int main() {
  int arg1 = 44;
  int arg2 = 2;
  int sub = 0;
  __asm__("subl %%ebx, %%eax;" : "=a"(sub) : "a"(arg1), "b"(arg2));
  return sub;
}
