#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");

  double sum = 0;
  sum += polyglot_as_i32(polyglot_invoke(obj, "addI", (int) 1));         // 4
  sum += polyglot_as_i8(polyglot_invoke(obj, "addB", (char) 2));         // 3
  sum += polyglot_as_i64(polyglot_invoke(obj, "addL", (long) 3));        // 7
  sum += polyglot_as_float(polyglot_invoke(obj, "addF", (float) 4.5));   // 10
  sum += polyglot_as_double(polyglot_invoke(obj, "addD", (double) 5.5)); // 12
  return sum;                                                            // 36
}
