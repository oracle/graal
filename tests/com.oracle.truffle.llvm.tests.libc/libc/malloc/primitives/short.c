#include <stdlib.h>

int main() {
  volatile short *s = malloc(sizeof(short));
  *s = 23;
  return *s;
}
