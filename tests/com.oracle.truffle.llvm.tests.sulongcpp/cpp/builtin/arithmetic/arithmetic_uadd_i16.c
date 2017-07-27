#ifndef __clang__
unsigned short __builtin_addcs(unsigned short, unsigned short, unsigned short, unsigned short*);
#endif

int main(int argc, const char **argv) {
  unsigned short carryout;

  __builtin_addcs((unsigned short)0x0, (unsigned short)0x0, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcs((unsigned short)0xFFFF, (unsigned short)0x0, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcs((unsigned short)0x0, (unsigned short)0xFFFF, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcs((unsigned short)0xFFFF, (unsigned short)0x1, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcs((unsigned short)0x1, (unsigned short)0xFFFF, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcs((unsigned short)0xFFFF, (unsigned short)0xFFFF, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcs((unsigned short)0x0, (unsigned short)0xFFFE, 1, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcs((unsigned short)0x0, (unsigned short)0xFFFF, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcs((unsigned short)0xFFFE, (unsigned short)0x0, 1, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcs((unsigned short)0xFFFF, (unsigned short)0x0, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcs((unsigned short)0xFFFF, (unsigned short)0xFFFF, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  unsigned short res1 = __builtin_addcs((unsigned short)0x0FFF, (unsigned short)0x1, 0, &carryout);
  if (res1 != 0x1000 || carryout != 0) {
    return -1;
  }

  unsigned short res2 = __builtin_addcs((unsigned short)0x0FFF, (unsigned short)0x1, 1, &carryout);
  if (res2 != 0x1001 || carryout != 0) {
    return -1;
  }

  return 0;
}
