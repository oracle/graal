#include <polyglot.h>

int main() {
  void (*fn)(void *) = polyglot_import("foo");
  fn(polyglot_from_string("bar", "ascii"));
  return 14;
}
