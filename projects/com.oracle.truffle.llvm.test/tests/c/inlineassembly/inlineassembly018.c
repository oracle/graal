int main() {
  int arg1 = 0;
  int arg2 = 0;
  int calc = 0;
  __asm__("movl $2, %%eax;"
          "movl $10, %%ebx;"
          "addl %%eax, %%ebx;"
          : "=b"(calc)
          : "a"(arg1), "b"(arg2));
  return calc;
}
