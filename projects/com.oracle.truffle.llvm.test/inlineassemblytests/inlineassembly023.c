int main() {
  int arg1 = 40;
  int arg2 = 2;
  int quot = 0;
  __asm__("idivl %%ebx;" : "=a"(quot): "a"(arg1), "d"(0), "b"(arg2));
  return quot;
}
