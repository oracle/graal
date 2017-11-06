#include <stdlib.h>

int main() {
  TYPE *val = malloc(sizeof(TYPE));
  *val = 4;
  TYPE result = *val;
  free(val);
  return result;
}
