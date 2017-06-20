
#include <stdlib.h>

int *p[5];

int main() {

  int i;
  for (i = 0; i < 5; i++) {
    p[i] = (int *)malloc(8 * 100);
  }

  for (i = 4; i >= 0; i--) {
    free(p[i]);
  }
  return 0;
}
