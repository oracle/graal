#ifndef __clang__
#include <stdbool.h>
bool __builtin_mul_overflow(unsigned char, unsigned char, unsigned char*);
#endif

int main(int argc, const char **argv) {
  unsigned char res;

  if (__builtin_mul_overflow((unsigned char)0x0, (unsigned char)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x0, (unsigned char)0x7F, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x0, (unsigned char)0x80, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x1, (unsigned char)0x7F, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x1, (unsigned char)0x80, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x2, (unsigned char)0x3F, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0x2, (unsigned char)0xC0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x2, (unsigned char)0x7F, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0x2, (unsigned char)0x80, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0x4, (unsigned char)0x7F, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0x4, (unsigned char)0x80, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x0F, (unsigned char)0x8, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x10, (unsigned char)0x8, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0x10, (unsigned char)0x10, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x7F, (unsigned char)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x7F, (unsigned char)0x1, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x7F, (unsigned char)0x2, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0x7F, (unsigned char)0x4, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0x7F, (unsigned char)0x7F, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0x7F, (unsigned char)0x80, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x80, (unsigned char)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0x80, (unsigned char)0x1, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0x80, (unsigned char)0x2, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0x80, (unsigned char)0x7F, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0x80, (unsigned char)0x80, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0xFF, (unsigned char)0x0, &res)) {
    return -1;
  }

  if (res != 0x0) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned char)0xFF, (unsigned char)0x1, &res)) {
    return -1;
  }

  if (res != 0xFF) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned char)0xFF, (unsigned char)0xFF, &res)) {
    return -1;
  }

  return 0;
}
