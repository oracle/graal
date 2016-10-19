int main() {
  int arg1 = 40;
  int arg2 = 2;
  int quot = 0;
  __asm__("idivl $5;" : "=a"(quot): "a"(arg1), "d"(0));
  return quot;
}
