#include <stdlib.h>

int a __attribute__((weak)) = 3;

int main() {
  if (a != 3) {
    abort();
  }
}
