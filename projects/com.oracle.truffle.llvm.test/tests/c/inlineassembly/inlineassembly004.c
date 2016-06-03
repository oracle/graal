int main() {
  int arg1 = 5;
  int arg2 = 9;
  int and = 0;
  __asm__("andl %%ebx, %%eax;" : "=a"(and) : "a"(arg1), "b"(arg2));
  return and;
}
