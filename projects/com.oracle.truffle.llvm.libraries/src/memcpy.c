#include <stddef.h>

void *truffle_managed_memcpy(void *destination, const void *source, size_t count) {
  for (size_t n = 0; n < count; n++) {
    ((char *)destination)[n] = ((char *)source)[n];
  }
  return destination;
}
