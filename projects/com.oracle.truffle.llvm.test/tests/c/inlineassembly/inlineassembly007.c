int main() {
  int arg1 = 190;
  int dec = 0;
  __asm__("decl %%eax;" : "=a"(dec) : "a"(arg1));
  return dec;
}
