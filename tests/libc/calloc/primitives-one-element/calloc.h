#include <stdlib.h>

int main() {
  TYPE *val = calloc(1, sizeof(TYPE));
  *val = 3;
  return *val;
}
