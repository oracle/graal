#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");
  void *ret = polyglot_get_member(obj, "valueI");
  return polyglot_as_i32(ret);
}
