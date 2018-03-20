#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");
  if (polyglot_has_array_elements(obj)) {
    return 42;
  } else {
    return 13;
  }
}
