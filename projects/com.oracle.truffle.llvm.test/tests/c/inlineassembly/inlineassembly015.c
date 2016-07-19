int main() {
  int arg1 = -105;
  int shr = 0;
  __asm__("shrl $4, %%eax;" : "=a"(shr) : "a"(arg1));
  return shr;
}
