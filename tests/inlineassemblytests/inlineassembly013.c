int main() {
  int mov = 0;
  int arg1 = 2;
  __asm__("movl $0xFF, %%eax;" : "=a"(mov) : "a"(arg1));
  return mov;
}
