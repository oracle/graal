#include "cpuid.h"

int main() {
  unsigned int out = 0;
  unsigned int i;
  unsigned char cf;
  unsigned int old = out;

  if(!has_rdseed())
    return 1;

  for(i = 0; i < 2; i++) {
    unsigned int tmp = out;
    __asm__("rdseed %%eax; setc %%dl" : "=a"(out), "=d"(cf));
    if(cf)
      old = tmp;
    else {
      i--;
      continue;
    }
    if(old != out)
      return 1;
  }

  return 0;
}
