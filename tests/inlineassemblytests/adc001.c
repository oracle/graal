#include <stdio.h>
#include "flags.h"

void test_adc(int a, int b, int cf) {
  long flags = cf ? CC_C : 0;
  long out_flags;
  int out;
  __asm__ volatile("pushf\n"
		   "push %%rax\n"
		   "popf\n"
		   "adcl %[a], %[b]\n"
		   "pushf\n"
		   "pop %%rax\n"
		   "popf\n"
		   :     "=a"(out_flags),
		     [b] "=r" (out)
		   : [a] "r"(a),
		         "1"(b),
		         "a"(flags));
  printf("%08x:%08x:%x:%08x:%x:%x\n", a, b, cf, out, (out_flags & CC_C) ? 1 : 0, (out_flags & CC_O) ? 1 : 0);
}

int main() {
  test_adc(0x00000000, 0x00000000, 0x0);
  test_adc(0x00000000, 0x00000000, 0x1);
  test_adc(0x00000d0c, 0x00000000, 0x1);
  test_adc(0x00000d0c, 0x00000d0c, 0x1);
  test_adc(0x00000000, 0x00000d0c, 0x1);
  test_adc(0x00000d0c, 0x00000000, 0x0);
  test_adc(0x00000d0c, 0x00000d0c, 0x0);
  test_adc(0x00000000, 0x00000d0c, 0x0);
  test_adc(0xffffffff, 0x00000000, 0x0);
  test_adc(0xffffffff, 0x00000001, 0x0);
  test_adc(0xffffffff, 0x00000d0c, 0x0);
  test_adc(0xffffffff, 0x80000000, 0x0);
  test_adc(0xffffffff, 0xffffffff, 0x0);
  test_adc(0xffffffff, 0x00000000, 0x1);
  test_adc(0xffffffff, 0x00000001, 0x1);
  test_adc(0xffffffff, 0x00000d0c, 0x1);
  test_adc(0xffffffff, 0x80000000, 0x1);
  test_adc(0xffffffff, 0xffffffff, 0x1);
  test_adc(0x80000000, 0x00000000, 0x0);
  test_adc(0x80000000, 0x00000d0c, 0x0);
  test_adc(0x80000000, 0x80000000, 0x0);
  test_adc(0x80000000, 0xffffffff, 0x0);
  test_adc(0x80000000, 0x00000000, 0x1);
  test_adc(0x80000000, 0x00000d0c, 0x1);
  test_adc(0x80000000, 0x80000000, 0x1);
  test_adc(0x80000000, 0xffffffff, 0x1);
}
