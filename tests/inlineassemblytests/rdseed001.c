#include "cpuid.h"

int main() {
  unsigned short out = 0;
  unsigned int i;
  unsigned char cf;
  unsigned short old = out;

  if(!has_rdseed())
    return 1;

  for(i = 0; i < 4; i++) {
    unsigned short tmp = out;
    __asm__("rdseed %%ax; setc %%dl" : "=a"(out), "=d"(cf));
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
