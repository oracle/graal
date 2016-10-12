int main() {
  int arg1 = 42;
  int inc = 0;
  __asm__("incl %%eax;" : "=a"(inc) : "a"(arg1));
  return inc;
}
