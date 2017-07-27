#ifndef __clang__
unsigned char __builtin_addcb(unsigned char, unsigned char, unsigned char, unsigned char*);
#endif

int main(int argc, const char **argv) {
  unsigned char carryout;

  __builtin_addcb((unsigned char)0x0, (unsigned char)0x0, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcb((unsigned char)0xFF, (unsigned char)0x0, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcb((unsigned char)0x0, (unsigned char)0xFF, 0, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcb((unsigned char)0xFF, (unsigned char)0x1, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcb((unsigned char)0x1, (unsigned char)0xFF, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcb((unsigned char)0xFF, (unsigned char)0xFF, 0, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcb((unsigned char)0x0, (unsigned char)0xFE, 1, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcb((unsigned char)0x0, (unsigned char)0xFF, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcb((unsigned char)0xFE, (unsigned char)0x0, 1, &carryout);
  if (carryout != 0) {
    return -1;
  }

  __builtin_addcb((unsigned char)0xFF, (unsigned char)0x0, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  __builtin_addcb((unsigned char)0xFF, (unsigned char)0xFF, 1, &carryout);
  if (carryout != 1) {
    return -1;
  }

  unsigned char res1 = __builtin_addcb((unsigned char)0x0F, (unsigned char)0x1, 0, &carryout);
  if (res1 != 0x10 || carryout != 0) {
    return -1;
  }

  unsigned char res2 = __builtin_addcb((unsigned char)0x0F, (unsigned char)0x1, 1, &carryout);
  if (res2 != 0x11 || carryout != 0) {
    return -1;
  }

  return 0;
}
