#include <stdlib.h>

int main() {
  TYPE *val = calloc(5, sizeof(TYPE));
  val[4] = 123;
  return val[4];
}
