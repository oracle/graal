#ifndef __clang__
unsigned int __builtin_addc(unsigned int, unsigned int, unsigned int, unsigned int*);
#endif

int main(int argc, const char **argv) {
  unsigned int carryout;

  __builtin_addc((unsigned int)0x0, (unsigned int)0x0, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addc((unsigned int)0xFFFFFFFF, (unsigned int)0x0, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addc((unsigned int)0x0, (unsigned int)0xFFFFFFFF, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addc((unsigned int)0xFFFFFFFF, (unsigned int)0x1, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addc((unsigned int)0x1, (unsigned int)0xFFFFFFFF, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addc((unsigned int)0xFFFFFFFF, (unsigned int)0xFFFFFFFF, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addc((unsigned int)0x0, (unsigned int)0xFFFFFFFE, 1, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addc((unsigned int)0x0, (unsigned int)0xFFFFFFFF, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addc((unsigned int)0xFFFFFFFE, (unsigned int)0x0, 1, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addc((unsigned int)0xFFFFFFFF, (unsigned int)0x0, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addc((unsigned int)0xFFFFFFFF, (unsigned int)0xFFFFFFFF, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  unsigned int res1 = __builtin_addc((unsigned int)0x0FFFFFFF, (unsigned int)0x1, 0, &carryout);
  if (res1 != 0x10000000 || carryout != 0) {
    return -1;
  }

  unsigned int res2 = __builtin_addc((unsigned int)0x0FFFFFFF, (unsigned int)0x1, 1, &carryout);
  if (res2 != 0x10000001 || carryout != 0) {
    return -1;
  }

  return 0;
}
