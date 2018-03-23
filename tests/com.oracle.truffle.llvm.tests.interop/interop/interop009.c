#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");
  int (*fn)(int, int) = (int (*)(int, int)) obj;
  return fn(40, 2);
}
