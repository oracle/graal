#include <stdlib.h>

int main() {
  TYPE *test = malloc(sizeof(TYPE) * 3);
  test[1] = 9;
  free(test);
  return 0;
}
