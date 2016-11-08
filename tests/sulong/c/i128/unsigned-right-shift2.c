#include <stdio.h>
#include <stdlib.h>

struct asdf {
  long a;
  long b;
};

int main() {
  volatile __uint128_t val = -1;
  volatile __uint128_t val2 = val >> 30;
  volatile struct asdf *ptr = (struct asdf *)&val2;
  if (ptr->a != -1 || ptr->b != 17179869183) {
    abort();
  }
}
