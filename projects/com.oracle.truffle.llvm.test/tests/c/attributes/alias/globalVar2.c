#include <stdlib.h>

int original = 1324342;
extern int alias __attribute__((alias("original")));

int main() {
  if (alias != original) {
    abort();
  }
  return 0;
}
