int main() {
  int arg1 = 26;
  int shl = 0;
  __asm__("shll $0x2, %%eax;" : "=a"(shl) : "a"(arg1));
  return shl;
}
