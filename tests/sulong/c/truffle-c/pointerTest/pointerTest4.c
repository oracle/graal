
#include <stdlib.h>

int main() {
  int *pm;
  pm = (int *)malloc(5 * 8);
  int *pc;
  pc = (int *)calloc(5, 8);

  int i;
  for (i = 0; i < 5; i++) {
    *(pm + i) = i;
    *(pc + i) = i;
  }
  for (i = 0; i < 5; i++) {
    if (*(pm + i) != *(pc + i)) {
      return -1;
    }
  }
  free(pc);
  free(pm);
  return 0;
}
