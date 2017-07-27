#ifndef __clang__
#include <stdbool.h>
bool __builtin_mul_overflow(unsigned long, unsigned long, unsigned long*);
#endif

int main(int argc, const char **argv) {
  unsigned long res;

#ifndef __clang__
  #warning "Disable testcase for non clang compiler!"
  return 0;
#endif

  if (__builtin_mul_overflow((unsigned long)0x0, (unsigned long)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x0, (unsigned long)0x7FFFFFFFFFFFFFFF, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x0, (unsigned long)0x8000000000000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x1, (unsigned long)0x7FFFFFFFFFFFFFFF, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x1, (unsigned long)0x8000000000000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x2, (unsigned long)0x3FFFFFFFFFFFFFFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0x2, (unsigned long)0xC000000000000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x2, (unsigned long)0x7FFFFFFFFFFFFFFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0x2, (unsigned long)0x8000000000000000, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0x4, (unsigned long)0x7FFFFFFFFFFFFFFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0x4, (unsigned long)0x8000000000000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x0F, (unsigned long)0x8, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x1000000000000000, (unsigned long)0x8, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0x1000000000000000, (unsigned long)0x1000000000000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x7FFFFFFFFFFFFFFF, (unsigned long)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x7FFFFFFFFFFFFFFF, (unsigned long)0x1, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x7FFFFFFFFFFFFFFF, (unsigned long)0x2, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0x7FFFFFFFFFFFFFFF, (unsigned long)0x4, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0x7FFFFFFFFFFFFFFF, (unsigned long)0x7FFFFFFFFFFFFFFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0x7FFFFFFFFFFFFFFF, (unsigned long)0x8000000000000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x8000000000000000, (unsigned long)0x0, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0x8000000000000000, (unsigned long)0x1, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0x8000000000000000, (unsigned long)0x2, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0x8000000000000000, (unsigned long)0x7FFFFFFFFFFFFFFF, &res)) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0x8000000000000000, (unsigned long)0x8000000000000000, &res)) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0x0, &res)) {
    return -1;
  }

  if (res != 0x0) {
    return -1;
  }

  if (__builtin_mul_overflow((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0x1, &res)) {
    return -1;
  }

  if (res != 0xFFFFFFFFFFFFFFFF) {
    return -1;
  }

  if (!__builtin_mul_overflow((unsigned long)0xFFFFFFFFFFFFFFFF, (unsigned long)0xFFFFFFFFFFFFFFFF, &res)) {
    return -1;
  }

  return 0;
}
