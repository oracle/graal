#include <stdlib.h>

int original = 1324342;
extern int alias __attribute__((alias("original")));

int main() {
  original = 3;
  if (alias != 3) {
    abort();
  }
  return 0;
}
