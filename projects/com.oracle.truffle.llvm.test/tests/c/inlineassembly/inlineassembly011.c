int main() {
  int arg1 = 56;
  int arg2 = 20;
  int arg3 = 21;
  int calc = 0;
  __asm__("addl %%ebx, %%eax;"
          "subl %%ecx, %%eax;"
          : "=a"(calc)
          : "a"(arg1), "b"(arg2), "c"(arg3));
  return calc;
}
