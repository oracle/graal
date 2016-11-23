int main() {
  int arg1 = 68;
  int arg2 = 0;
  int mov = 0;
  __asm__("movl %%ecx, %%eax;" : "=a"(mov) : "c"(arg1), "a"(arg2));
  return mov;
}
