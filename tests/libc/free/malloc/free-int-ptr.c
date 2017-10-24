#include <stdlib.h>

int main() {
  int **val = malloc(sizeof(int *));
  int result = 5;
  *val = &result;
  int ret = **val;
  free(val);
  return ret;
}
