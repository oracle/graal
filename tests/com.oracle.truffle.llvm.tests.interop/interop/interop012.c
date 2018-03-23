#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");
  double (*fn)(double, double) = (double (*)(double, double)) obj;
  return (int) fn(40.5, 1.5);
}
