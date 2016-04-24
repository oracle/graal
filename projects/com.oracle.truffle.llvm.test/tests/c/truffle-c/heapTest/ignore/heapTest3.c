
#include <stdlib.h>

int *p[100];

int main() {

  int i;

  for (i = 0; i < 100; i++) {
    p[i] = (int *)malloc(8 * 100);
  }

  for (i = 0; i < 100; i++) {
    p[i] = (int *)realloc(p[i], 8 * 300);
  }

  for (i = 0; i < 100; i++) {
    p[i] = (int *)realloc(p[i], 8 * 30);
  }

  for (i = 99; i >= 0; i--) {
    free(p[i]);
  }

  return 9;
}
