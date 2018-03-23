#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");

  void *i = polyglot_get_member(obj, "valueI");
  void *c = polyglot_get_member(obj, "valueB");
  void *l = polyglot_get_member(obj, "valueL");
  void *f = polyglot_get_member(obj, "valueF");
  void *d = polyglot_get_member(obj, "valueD");

  double sum = 0;
  sum += polyglot_as_i32(polyglot_get_array_element(i, 0));
  sum += polyglot_as_i64(polyglot_get_array_element(l, 0));
  sum += polyglot_as_i8(polyglot_get_array_element(c, 0));
  sum += polyglot_as_float(polyglot_get_array_element(f, 0));
  sum += polyglot_as_double(polyglot_get_array_element(d, 0));

  sum += polyglot_as_i32(polyglot_get_array_element(i, 1));
  sum += polyglot_as_i64(polyglot_get_array_element(l, 1));
  sum += polyglot_as_i8(polyglot_get_array_element(c, 1));
  sum += polyglot_as_float(polyglot_get_array_element(f, 1));
  sum += polyglot_as_double(polyglot_get_array_element(d, 1));

  // 73
  return (int)sum;
}
