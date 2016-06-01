#include <stdlib.h>

int main() {
  int arg1 = 0;
  int not = 0;
  __asm__("notl %%eax;" : "=a"(not) : "a"(arg1));

  if (not != -1) {
    abort();
  }
  return 0;
}
