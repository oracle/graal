#include <stdlib.h>

int main() {
  int arg1 = -105;
  int sar = 0;
  __asm__("sarl $4, %%eax;" : "=a"(sar) : "a"(arg1));

  if (sar != -7) {
    abort();
  }
  return 0;
}
