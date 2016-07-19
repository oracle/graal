int main() {
  int arg1 = -105;
  int sar = 0;
  __asm__("sarl $4, %%eax;" : "=a"(sar) : "a"(arg1));
  return sar;
}
