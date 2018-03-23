#include <polyglot.h>

int main() {
  foo("foreign");
  return foo("foreign2");
}

int foo(const char *name) {
  void *obj = polyglot_import(name);
  return polyglot_as_i32(polyglot_get_member(obj, "valueI"));
}
