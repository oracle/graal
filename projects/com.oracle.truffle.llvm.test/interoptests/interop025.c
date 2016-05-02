#include <truffle.h>

int main() {
  foo("foreign");
  foo("foreign2");
  return foo("foreign3");
}

int foo(const char *name) {
  void *obj = truffle_import_cached(name);
  return truffle_read_i(obj, "valueI");
}
