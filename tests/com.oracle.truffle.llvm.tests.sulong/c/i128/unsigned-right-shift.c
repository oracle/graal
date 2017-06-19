#include <stdio.h>
#include <stdlib.h>

struct asdf {
  long a;
  long b;
};

int main() {
  volatile __uint128_t val = -1;
  volatile __uint128_t val2 = val >> 90;
  volatile struct asdf *ptr = (struct asdf *)&val2;
  if (ptr->a != 274877906943 || ptr->b != 0) {
    abort();
  }
}
