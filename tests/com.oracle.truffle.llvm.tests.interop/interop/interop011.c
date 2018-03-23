#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");
  float (*fn)(float, float) = (float (*)(float, float)) obj;
  return (int) fn(40.5, 1.5);
}
