#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");
  long (*fn)(long, long) = (long (*)(long, long)) obj;
  return (int) fn(40, 2);
}
