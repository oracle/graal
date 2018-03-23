#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");

  polyglot_put_member(obj, "valueI", (int) 2);      // 32 bit
  polyglot_put_member(obj, "valueB", (char) 3);     // char = 8 bit in C ; byte = 8 bit in Java
  polyglot_put_member(obj, "valueL", (long) 4);     // 64 bit
  polyglot_put_member(obj, "valueF", (float) 5.5);  // 32 bit
  polyglot_put_member(obj, "valueD", (double) 6.5); // 64 bit
  return 0;
}
