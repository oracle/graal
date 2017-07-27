#ifndef __clang__
#include <stdbool.h>
bool __builtin_mul_overflow(unsigned int, unsigned int, unsigned int*);
#endif

int main(int argc, const char **argv) {
  unsigned int res;

#ifndef __clang__
  #warning "Disable testcase for non clang compiler!"
  return 0;
#endif

  if (__builtin_mul_overflow((unsigned int)0x0, (unsigned int)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x0, (unsigned int)0x7FFFFFFF, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x0, (unsigned int)0x80000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x1, (unsigned int)0x7FFFFFFF, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x1, (unsigned int)0x80000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x2, (unsigned int)0x3FFFFFFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0x2, (unsigned int)0xC0000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x2, (unsigned int)0x7FFFFFFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0x2, (unsigned int)0x80000000, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0x4, (unsigned int)0x7FFFFFFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0x4, (unsigned int)0x80000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x0F, (unsigned int)0x8, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x10000000, (unsigned int)0x8, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0x10000000, (unsigned int)0x10000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x7FFFFFFF, (unsigned int)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x7FFFFFFF, (unsigned int)0x1, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x7FFFFFFF, (unsigned int)0x2, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0x7FFFFFFF, (unsigned int)0x4, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0x7FFFFFFF, (unsigned int)0x7FFFFFFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0x7FFFFFFF, (unsigned int)0x80000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x80000000, (unsigned int)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0x80000000, (unsigned int)0x1, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0x80000000, (unsigned int)0x2, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0x80000000, (unsigned int)0x7FFFFFFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0x80000000, (unsigned int)0x80000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0xFFFFFFFF, (unsigned int)0x0, &res)) {
    return -1;
  }

  if (res != 0x0) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned int)0xFFFFFFFF, (unsigned int)0x1, &res)) {
    return -1;
  }

  if (res != 0xFFFFFFFF) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned int)0xFFFFFFFF, (unsigned int)0xFFFFFFFF, &res)) {
    return -1;
  }

  return 0;
}
