#include <stdlib.h>

char a;
long b;
char c;
int d;
char e;
int f[10];

int main() {
  if ((long)&b % __alignof__(long)!= 0) {
    abort();
  }
  if ((long)&d % __alignof__(int)!= 0) {
    abort();
  }
  if ((long)&f % __alignof__(int[10]) != 0) {
    abort();
  }
  return 0;
}
