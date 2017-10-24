#include <stdlib.h>

int main() {
  int *val = calloc(1, sizeof(int));
  free(val);
}
