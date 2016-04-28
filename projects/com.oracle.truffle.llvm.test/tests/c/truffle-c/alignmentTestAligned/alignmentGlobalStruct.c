#include <stdlib.h>

char a;

struct {
  int a;
} test;

int main() {
  if ((long)&test % __alignof__(test) != 0) {
    abort();
  }
  return 0;
}
