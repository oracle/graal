
#include <stdlib.h>

int main() {
  int i;
  int *p;
  for (i = 0; i < 1000; i++) {
    p = (int *)malloc(10 * 8);
    free(p);
  }
  return 0;
}
