#include <polyglot.h>

int main() {
  foo("foreign");
  foo("foreign2");
  return foo("foreign3");
}

int foo(const char *name) {
  void *obj = polyglot_import(name);
  return polyglot_as_i32(polyglot_get_member(obj, "valueI"));
}
