#ifndef __clang__
unsigned char __builtin_subcb(unsigned char, unsigned char, unsigned char, unsigned char*);
#endif

int main(int argc, const char **argv) {
  unsigned char carryout, res;

  res = __builtin_subcb((unsigned char)0x0, (unsigned char)0x0, 0, &carryout);
  if (res != 0x0 || carryout != 0) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0xFF, (unsigned char)0x0, 0, &carryout);
  if (res != 0xFF || carryout != 0) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0x0, (unsigned char)0xFF, 0, &carryout);
  if (res != 0x01 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0xFF, (unsigned char)0x1, 0, &carryout);
  if (res != 0xFE || carryout != 0) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0x1, (unsigned char)0xFF, 0, &carryout);
  if (res != 0x2 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0xFF, (unsigned char)0xFF, 0, &carryout);
  if (res != 0x0 || carryout != 0) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0x8F, (unsigned char)0x0F, 0, &carryout);
  if (res != 0x80 || carryout != 0) {
    return res;
  }

  res = __builtin_subcb((unsigned char)0x0, (unsigned char)0xFE, 1, &carryout);
  if (res != 0x1 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0x0, (unsigned char)0xFF, 1, &carryout);
  if (res != 0x0 || carryout != 1) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0xFE, (unsigned char)0x0, 1, &carryout);
  if (res != 0xFD || carryout != 0) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0xFE, (unsigned char)0xFE, 1, &carryout);
  if (res != 0xFF || carryout != 1) {
    return res;
  }

  res = __builtin_subcb((unsigned char)0xFE, (unsigned char)0xFF, 0, &carryout);
  if (res != 0xFF || carryout != 1) {
    return res;
  }

  res = __builtin_subcb((unsigned char)0xFE, (unsigned char)0xFF, 1, &carryout);
  if (res != 0xFE || carryout != 1) {
    return res;
  }

  res = __builtin_subcb((unsigned char)0xFF, (unsigned char)0x0, 1, &carryout);
  if (res != 0xFE || carryout != 0) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0xFF, (unsigned char)0xFF, 1, &carryout);
  if (res != 0xFF || carryout != 1) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0x0F, (unsigned char)0x1, 0, &carryout);
  if (res != 0x0E || carryout != 0) {
    return -1;
  }

  res = __builtin_subcb((unsigned char)0x0F, (unsigned char)0x1, 1, &carryout);
  if (res != 0x0D || carryout != 0) {
    return -1;
  }

  return 0;
}
