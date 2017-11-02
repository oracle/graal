#include <stdlib.h>

int main() {
  volatile long *l = malloc(sizeof(long));
  *l = 2343462436456;
  return *l % 256;
}
