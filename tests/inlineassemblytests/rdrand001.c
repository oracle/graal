#include "cpuid.h"

int main() {
  unsigned short out = 0;
  unsigned int i;
  unsigned char cf;
  unsigned short old = out;

  if(!has_rdrand())
    return 1;

  for(i = 0; i < 32; i++) {
    unsigned short tmp = out;
    __asm__("rdrand %%ax; setc %%dl" : "=a"(out), "=d"(cf));
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
