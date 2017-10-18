#include <stdio.h>
#include "flags.h"

void test_add(int a, int b) {
  int out_flags;
  int out;
  __asm__ volatile("pushf\n"
		   "xorq %%rax, %%rax\n"
		   "push %%rax\n"
		   "popf\n"
		   "addl %[a], %[b]\n"
		   "pushf\n"
		   "pop %%rax\n"
		   "popf\n"
		   :     "=a"(out_flags),
		     [b] "=r" (out)
		   : [a] "r"(a),
		         "1"(b));
  printf("%08x:%08x:%08x:%x:%x\n", a, b, out, (out_flags & CC_C) ? 1 : 0, (out_flags & CC_O) ? 1 : 0);
}

int main() {
  test_add(0x00000000, 0x00000000);
  test_add(0x00000000, 0x00000d0c);
  test_add(0x00000d0c, 0x00000000);
  test_add(0x00000d0c, 0x00000d0c);
  test_add(0xffffffff, 0x00000000);
  test_add(0xffffffff, 0x00000001);
  test_add(0xffffffff, 0x00000d0c);
  test_add(0xffffffff, 0x80000000);
  test_add(0xffffffff, 0xffffffff);
  test_add(0x80000000, 0x00000000);
  test_add(0x80000000, 0x00000d0c);
  test_add(0x80000000, 0x80000000);
  test_add(0x80000000, 0xffffffff);
}
