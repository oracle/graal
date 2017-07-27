#ifndef __clang__
unsigned long __builtin_addcl(unsigned long, unsigned long, unsigned long, unsigned long*);
#endif

int main(int argc, const char **argv) {
  unsigned long carryout;

  __builtin_addcl((unsigned long)0x0, (unsigned long)0x0, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcl((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0x0, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcl((unsigned long)0x0, (unsigned long)0xFFFFFFFFFFFFFFFF, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcl((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0x1, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcl((unsigned long)0x1, (unsigned long)0xFFFFFFFFFFFFFFFF, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcl((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0xFFFFFFFFFFFFFFF, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcl((unsigned long)0x0, (unsigned long)0xFFFFFFFFFFFFFFFE, 1, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcl((unsigned long)0x0, (unsigned long)0xFFFFFFFFFFFFFFFF, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcl((unsigned long)0xFFFFFFFFFFFFFFFE, (unsigned long)0x0, 1, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcl((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0x0, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcl((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0xFFFFFFFFFFFFFFFF, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  long res1 = __builtin_addcl((unsigned long)0x0FFFFFFFFFFFFFFF, (unsigned long)0x1, 0, &carryout);
  if (res1 != 0x1000000000000000 || carryout != 0) {
    return -1;
  }

  long res2 = __builtin_addcl((unsigned long)0x0FFFFFFFFFFFFFFF, (unsigned long)0x1, 1, &carryout);
  if (res2 != 0x1000000000000001 || carryout != 0) {
    return -1;
  }

  return 0;
}
