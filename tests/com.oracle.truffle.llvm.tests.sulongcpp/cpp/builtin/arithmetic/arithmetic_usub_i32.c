#ifndef __clang__
unsigned int __builtin_subc(unsigned int, unsigned int, unsigned int, unsigned int*);
#endif

int main(int argc, const char **argv) {
  unsigned int carryout, res;

  res = __builtin_subc((unsigned int)0x0, (unsigned int)0x0, 0, &carryout);
  if (res != 0x0 || carryout != 0) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0xFFFFFFFF, (unsigned int)0x0, 0, &carryout);
  if (res != 0xFFFFFFFF || carryout != 0) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0x0, (unsigned int)0xFFFFFFFF, 0, &carryout);
  if (res != 0x01 || carryout != 1) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0xFFFFFFFF, (unsigned int)0x1, 0, &carryout);
  if (res != 0xFFFFFFFE || carryout != 0) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0x1, (unsigned int)0xFFFFFFFF, 0, &carryout);
  if (res != 0x2 || carryout != 1) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0xFFFFFFFF, (unsigned int)0xFFFFFFFF, 0, &carryout);
  if (res != 0x0 || carryout != 0) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0x8FFFFFFF, (unsigned int)0x0FFFFFFF, 0, &carryout);
  if (res != 0x80000000 || carryout != 0) {
    return res;
  }

  res = __builtin_subc((unsigned int)0x0, (unsigned int)0xFFFFFFFE, 1, &carryout);
  if (res != 0x1 || carryout != 1) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0x0, (unsigned int)0xFFFFFFFF, 1, &carryout);
  if (res != 0x0 || carryout != 1) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0xFFFFFFFE, (unsigned int)0x0, 1, &carryout);
  if (res != 0xFFFFFFFD || carryout != 0) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0xFFFFFFFE, (unsigned int)0xFFFFFFFE, 1, &carryout);
  if (res != 0xFFFFFFFF || carryout != 1) {
    return res;
  }

  res = __builtin_subc((unsigned int)0xFFFFFFFE, (unsigned int)0xFFFFFFFF, 0, &carryout);
  if (res != 0xFFFFFFFF || carryout != 1) {
    return res;
  }

  res = __builtin_subc((unsigned int)0xFFFFFFFE, (unsigned int)0xFFFFFFFF, 1, &carryout);
  if (res != 0xFFFFFFFE || carryout != 1) {
    return res;
  }

  res = __builtin_subc((unsigned int)0xFFFFFFFF, (unsigned int)0x0, 1, &carryout);
  if (res != 0xFFFFFFFE || carryout != 0) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0xFFFFFFFF, (unsigned int)0xFFFFFFFF, 1, &carryout);
  if (res != 0xFFFFFFFF || carryout != 1) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0x0F, (unsigned int)0x1, 0, &carryout);
  if (res != 0x0E || carryout != 0) {
    return -1;
  }

  res = __builtin_subc((unsigned int)0x0F, (unsigned int)0x1, 1, &carryout);
  if (res != 0x0D || carryout != 0) {
    return -1;
  }

  return 0;
}
