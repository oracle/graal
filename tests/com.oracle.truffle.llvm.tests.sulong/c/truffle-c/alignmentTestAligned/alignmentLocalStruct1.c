#include <stdlib.h>

int main() {
  char a;

  struct {
    int a;
  } test;
  char *ptr = &a;
  if ((long)&test % __alignof__(test) != 0) {
    abort();
  }
  return 0;
}
