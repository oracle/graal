#ifndef __clang__
unsigned short __builtin_subcs(unsigned short, unsigned short, unsigned short, unsigned short*);
#endif

int main(int argc, const char **argv) {
  unsigned short carryout, res;

  res = __builtin_subcs((unsigned short)0x0, (unsigned short)0x0, 0, &carryout);
  if (res != 0x0 || carryout != 0) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0xFFFF, (unsigned short)0x0, 0, &carryout);
  if (res != 0xFFFF || carryout != 0) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0x0, (unsigned short)0xFFFF, 0, &carryout);
  if (res != 0x01 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0xFFFF, (unsigned short)0x1, 0, &carryout);
  if (res != 0xFFFE || carryout != 0) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0x1, (unsigned short)0xFFFF, 0, &carryout);
  if (res != 0x2 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0xFFFF, (unsigned short)0xFFFF, 0, &carryout);
  if (res != 0x0 || carryout != 0) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0x8FFF, (unsigned short)0x0FFF, 0, &carryout);
  if (res != 0x8000 || carryout != 0) {
    return res;
  }

  res = __builtin_subcs((unsigned short)0x0, (unsigned short)0xFFFE, 1, &carryout);
  if (res != 0x1 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0x0, (unsigned short)0xFFFF, 1, &carryout);
  if (res != 0x0 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0xFFFE, (unsigned short)0x0, 1, &carryout);
  if (res != 0xFFFD || carryout != 0) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0xFFFE, (unsigned short)0xFFFE, 1, &carryout);
  if (res != 0xFFFF || carryout != 1) {
    return res;
  }

  res = __builtin_subcs((unsigned short)0xFFFE, (unsigned short)0xFFFF, 0, &carryout);
  if (res != 0xFFFF || carryout != 1) {
    return res;
  }

  res = __builtin_subcs((unsigned short)0xFFFE, (unsigned short)0xFFFF, 1, &carryout);
  if (res != 0xFFFE || carryout != 1) {
    return res;
  }

  res = __builtin_subcs((unsigned short)0xFFFF, (unsigned short)0x0, 1, &carryout);
  if (res != 0xFFFE || carryout != 0) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0xFFFF, (unsigned short)0xFFFF, 1, &carryout);
  if (res != 0xFFFF || carryout != 1) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0x0F, (unsigned short)0x1, 0, &carryout);
  if (res != 0x0E || carryout != 0) {
    return -1;
  }

  res = __builtin_subcs((unsigned short)0x0F, (unsigned short)0x1, 1, &carryout);
  if (res != 0x0D || carryout != 0) {
    return -1;
  }

  return 0;
}
