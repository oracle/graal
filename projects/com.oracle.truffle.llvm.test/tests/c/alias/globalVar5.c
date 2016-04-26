#include <stdlib.h>

int original = 1324342;
extern int alias2 __attribute__((alias("alias")));
extern int alias3 __attribute__((alias("alias2")));
extern int alias __attribute__((alias("original")));

int main() {
  if (original != 1324342 || alias != 1324342 || alias2 != 1324342 || alias3 != 1324342) {
    abort();
  }
  alias2 = 4;
  if (original != 4 || alias != 4 || alias2 != 4 || alias3 != 4) {
    abort();
  }
  return 0;
}
