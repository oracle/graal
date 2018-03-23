#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");
  return polyglot_get_array_size(obj);
}
