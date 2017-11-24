#include <stdlib.h>

char a;

int main() {
  char b;
  long c;

  char *ptr = &b;
  if ((long)&a % __alignof__(char) != 0) {
    abort();
  }
  if ((long)&b % __alignof__(char) != 0) {
    abort();
  }
  if ((long)&c % __alignof__(long) != 0) {
    abort();
  }
  return 0;
}
