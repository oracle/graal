#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");
  return polyglot_as_i32(obj);
}
