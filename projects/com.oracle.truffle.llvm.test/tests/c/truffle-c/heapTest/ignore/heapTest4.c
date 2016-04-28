
#include <stdlib.h>

int main() {
  int *p = (int *)malloc(8);
  free(p);
  return 0;
}
