#ifndef __clang__
#include <stdbool.h>
bool __builtin_mul_overflow(unsigned short, unsigned short, unsigned short*);
#endif

int main(int argc, const char **argv) {
  unsigned short res;

  if (__builtin_mul_overflow((unsigned short)0x0, (unsigned short)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x0, (unsigned short)0x7FFF, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x0, (unsigned short)0x8000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x1, (unsigned short)0x7FFF, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x1, (unsigned short)0x8000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x2, (unsigned short)0x3FFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0x2, (unsigned short)0xC000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x2, (unsigned short)0x7FFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0x2, (unsigned short)0x8000, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0x4, (unsigned short)0x7FFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0x4, (unsigned short)0x8000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x0F, (unsigned short)0x8, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x1000, (unsigned short)0x8, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0x1000, (unsigned short)0x1000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x7FFF, (unsigned short)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x7FFF, (unsigned short)0x1, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x7FFF, (unsigned short)0x2, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0x7FFF, (unsigned short)0x4, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0x7FFF, (unsigned short)0x7FFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0x7FFF, (unsigned short)0x8000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x8000, (unsigned short)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0x8000, (unsigned short)0x1, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0x8000, (unsigned short)0x2, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0x8000, (unsigned short)0x7FFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0x8000, (unsigned short)0x8000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0xFFFF, (unsigned short)0x0, &res)) {
    return -1;
  }

  if (res != 0x0) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned short)0xFFFF, (unsigned short)0x1, &res)) {
    return -1;
  }

  if (res != 0xFFFF) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned short)0xFFFF, (unsigned short)0xFFFF, &res)) {
    return -1;
  }

  return 0;
}
