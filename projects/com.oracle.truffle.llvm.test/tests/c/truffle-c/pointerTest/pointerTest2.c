
#include <stdlib.h>

int main() {
  int **pa;
  pa = (int **)malloc(5 * 8);
  int *paa;
  int a = 5;
  paa = &a;
  pa[3] = paa;
  return *(*(pa + 3));
}
