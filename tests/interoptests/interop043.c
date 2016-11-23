#include <truffle.h>

static void **global;

int main() {
  void *object = truffle_import("foreign");

  global = (void **)truffle_managed_malloc(2 * sizeof(void *));

  global[0] = (void *)14;
  global[1] = object;

  if (global[0] != (void *)14)
    return 1;

  if (global[1] != object)
    return 1;

  void **local = (void **)truffle_managed_malloc(2 * sizeof(void *));

  local[0] = (void *)14;
  local[1] = object;

  if (local[0] != (void *)14)
    return 1;

  if (local[1] != object)
    return 1;

  return 0;
}
