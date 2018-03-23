#include <polyglot.h>
#include <truffle.h>

int main() {
  void *p = polyglot_import("object");
  void *p1 = truffle_handle_for_managed(p);
  long l_p1 = (long)p1;
  void *n = calloc(sizeof(char), 2);

  if (!truffle_is_handle_to_managed(p1)) {
      return 1;
  }
  if (!truffle_is_handle_to_managed(l_p1)) {
      return 2;
  }
  if (truffle_is_handle_to_managed(p)) {
      return 3;
  }
  if (truffle_is_handle_to_managed(n)) {
      return 4;
  }
  free(n);

  return 0;
}
