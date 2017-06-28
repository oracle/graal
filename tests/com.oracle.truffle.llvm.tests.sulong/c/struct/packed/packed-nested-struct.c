#include <stdlib.h>

#pragma pack(1)
struct nested {
  char x;
  int y;
  char z;
  char zz;
  char zzz;
};

#pragma pack(4)
struct test {
  struct nested s1;
  int a;
  char b;
  char c;
};

int main() {
  struct test t = { { 1, 2, 3 }, 4, 5 };
  long base = (long)&t;
  int a_offset = (long)&t.a - base;
  if (a_offset != 8) {
    abort();
  }
  int c_offset = (long)&t.c - base;
  if (c_offset != 13) {
    abort();
  }
}
