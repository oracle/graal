#include <stdlib.h>

int main() {
  volatile char *c = malloc(sizeof(char));
  *c = 'a';
  return *c;
}
