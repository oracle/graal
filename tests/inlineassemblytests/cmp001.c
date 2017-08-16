#include <stdio.h>
#include "flags.h"

int main() {
  char flags;
  __asm__("movb $0xFF, %%al; cmpb $0x02, %%al; lahf; movb %%ah, %%al" : "=a"(flags));
  printf("%02X\n", flags & CC_MASK8);
}
