#ifndef __clang__
#include <stdbool.h>
bool __builtin_add_overflow(signed short, signed short, signed short*);
#endif

int main(int argc, const char **argv) {
  signed short res;

  if (__builtin_add_overflow((signed short)0x0, (signed short)0x0, &res)) {
    return -1;
  }

  if (__builtin_add_overflow((signed short)0x7FFF, (signed short)0x0, &res)) {
    return -1;
  }

  if (__builtin_add_overflow((signed short)0x0, (signed short)0x7FFF, &res)) {
    return -1;
  }

  if (__builtin_add_overflow((signed short)0x8000, (signed short)0x7FFF, &res)) {
    return -1;
  }

  if (__builtin_add_overflow((signed short)0x7FFF, (signed short)0x8000, &res)) {
    return -1;
  }

  if (!__builtin_add_overflow((signed short)0x7FFF, (signed short)0x1, &res)) {
    return -1;
  }

  if (!__builtin_add_overflow((signed short)0x1, (signed short)0x7FFF, &res)) {
    return -1;
  }

  if (!__builtin_add_overflow((signed short)0x7FFF, (signed short)0x7FFF, &res)) {
    return -1;
  }

  if (!__builtin_add_overflow((signed short)0x8000, (signed short)0x8000, &res)) {
    return -1;
  }

  return 0;
}
