#define CF 1
#define PF (1 << 2)
#define ZF (1 << 6)
#define SF (1 << 7)
int main() {
  int arg1 = 0xBBBBBBBB;
  int arg2 = 0xDEADBEEF;
  char out = 0;
  __asm__("addl %1, %2; lahf; movb %%ah, %%al; xorb %%ah,%%ah;" : "=a"(out) : "r"(arg1), "r"(arg2));
  return (out & CF) && (out & PF) && (out & SF) ? 1 : 0;
}
