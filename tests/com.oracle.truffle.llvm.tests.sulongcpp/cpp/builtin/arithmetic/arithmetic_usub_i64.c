#ifndef __clang__
unsigned long __builtin_subcl(unsigned long, unsigned long, unsigned long, unsigned long*);
#endif

int main(int argc, const char **argv) {
  unsigned long carryout, res;

  res = __builtin_subcl((unsigned long)0x0, (unsigned long)0x0, 0, &carryout);
  if (res != 0x0 || carryout != 0) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0x0, 0, &carryout);
  if (res != 0xFFFFFFFFFFFFFFFF || carryout != 0) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0x0, (unsigned long)0xFFFFFFFFFFFFFFFF, 0, &carryout);
  if (res != 0x01 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0x1, 0, &carryout);
  if (res != 0xFFFFFFFFFFFFFFFE || carryout != 0) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0x1, (unsigned long)0xFFFFFFFFFFFFFFFF, 0, &carryout);
  if (res != 0x2 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0xFFFFFFFFFFFFFFFF, 0, &carryout);
  if (res != 0x0 || carryout != 0) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0x8FFFFFFFFFFFFFFF, (unsigned long)0x0FFFFFFFFFFFFFFF, 0, &carryout);
  if (res != 0x8000000000000000 || carryout != 0) {
    return res;
  }

  res = __builtin_subcl((unsigned long)0x0, (unsigned long)0xFFFFFFFFFFFFFFFE, 1, &carryout);
  if (res != 0x1 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0x0, (unsigned long)0xFFFFFFFFFFFFFFFF, 1, &carryout);
  if (res != 0x0 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0xFFFFFFFFFFFFFFFE, (unsigned long)0x0, 1, &carryout);
  if (res != 0xFFFFFFFFFFFFFFFD || carryout != 0) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0xFFFFFFFFFFFFFFFE, (unsigned long)0xFFFFFFFFFFFFFFFE, 1, &carryout);
  if (res != 0xFFFFFFFFFFFFFFFF || carryout != 1) {
    return res;
  }

  res = __builtin_subcl((unsigned long)0xFFFFFFFFFFFFFFFE, (unsigned long)0xFFFFFFFFFFFFFFFF, 0, &carryout);
  if (res != 0xFFFFFFFFFFFFFFFF || carryout != 1) {
    return res;
  }

  res = __builtin_subcl((unsigned long)0xFFFFFFFFFFFFFFFE, (unsigned long)0xFFFFFFFFFFFFFFFF, 1, &carryout);
  if (res != 0xFFFFFFFFFFFFFFFE || carryout != 1) {
    return res;
  }

  res = __builtin_subcl((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0x0, 1, &carryout);
  if (res != 0xFFFFFFFFFFFFFFFE || carryout != 0) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0xFFFFFFFFFFFFFFFF, 1, &carryout);
  if (res != 0xFFFFFFFFFFFFFFFF || carryout != 1) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0x0F, (unsigned long)0x1, 0, &carryout);
  if (res != 0x0E || carryout != 0) {
    return -1;
  }

  res = __builtin_subcl((unsigned long)0x0F, (unsigned long)0x1, 1, &carryout);
  if (res != 0x0D || carryout != 0) {
    return -1;
  }

  return 0;
}
