#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");

  void *i = polyglot_get_member(obj, "valueI");
  void *c = polyglot_get_member(obj, "valueB");
  void *l = polyglot_get_member(obj, "valueL");
  void *f = polyglot_get_member(obj, "valueF");
  void *d = polyglot_get_member(obj, "valueD");

  polyglot_set_array_element(i, 0, (int) 1);
  polyglot_set_array_element(i, 1, (int) 2);

  polyglot_set_array_element(l, 0, (long) 3);
  polyglot_set_array_element(l, 1, (long) 4);

  polyglot_set_array_element(c, 0, (short) 5);
  polyglot_set_array_element(c, 1, (short) 6);

  polyglot_set_array_element(f, 0, (float) 7.5);
  polyglot_set_array_element(f, 1, (float) 8.5);

  polyglot_set_array_element(d, 0, (double) 9.5);
  polyglot_set_array_element(d, 1, (double) 10.5);

  return 0;
}
