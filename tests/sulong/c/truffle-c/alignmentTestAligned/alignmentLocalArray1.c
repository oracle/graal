#include <stdlib.h>

char a;

int main() {
  short b;
  float c[10];

  short *ptr = &b;
  if ((long)&a % __alignof__(char)!= 0) {
    abort();
  }
  if ((long)&b % __alignof__(short)!= 0) {
    abort();
  }
  if ((long)&c % __alignof__(float[10]) != 0) {
    abort();
  }
  return 0;
}
