#include <stdlib.h>

int leading_one(int val) { return sizeof(int) * 8 - 1 - __builtin_clz(val); }

int leading_one_l(long val) { return sizeof(long) * 8 - 1 - __builtin_clzl(val); }

int main() {
  if (leading_one(0b00100101) != 5) {
    abort();
  }
  if (leading_one(1) != 0) {
    abort();
  }
  if (leading_one(-1) != 31) {
    abort();
  }
  if (leading_one_l(0b00100101) != 5) {
    abort();
  }
  if (leading_one_l(1) != 0) {
    abort();
  }
  if (leading_one_l(-1) != 63) {
    abort();
  }
}
