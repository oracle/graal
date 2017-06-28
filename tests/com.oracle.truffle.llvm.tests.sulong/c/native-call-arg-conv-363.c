#include <string.h>

int main(void) {
  char test[512];
  static void *(*const volatile memset_v)(void *, int, size_t) = &memset;
  memset_v(test, 0, 512);
  return 0;
}
