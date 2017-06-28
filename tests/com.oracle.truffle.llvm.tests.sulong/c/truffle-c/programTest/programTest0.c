
#include <stdlib.h>

int main() {
  int *pa;
  pa = (int *)malloc(5 * 8);

  int i = 0;
  for (i = 0; i < 5; i++) {
    *pa = i;
    pa++;
  }
  pa -= 5;
  return pa[4] + pa[3] + pa[2] + pa[1] + pa[0];
}
