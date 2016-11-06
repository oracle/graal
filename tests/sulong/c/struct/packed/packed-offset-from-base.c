#include <stdlib.h>

#pragma pack(1)
struct {
  char a;
  int b;
  short c;
  int d;
} test;

int main() {
  long base = (long)&test;
  int offset_a = (long)&test.a - base;
  int offset_b = (long)&test.b - base;
  int offset_c = (long)&test.c - base;
  int offset_d = (long)&test.d - base;
  if (offset_a != 0 || offset_b != 1 || offset_c != 5 || offset_d != 7) {
    abort();
  }
}
