#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");

  bool b = polyglot_as_boolean(polyglot_get_member(obj, "valueBool"));

  int i = polyglot_as_i32(polyglot_get_member(obj, "valueI"));       // 32 bit
  char c = polyglot_as_i8(polyglot_get_member(obj, "valueB"));       // char = 8 bit in C ; byte = 8 bit in Java
  long l = polyglot_as_i64(polyglot_get_member(obj, "valueL"));      // 64 bit
  float f = polyglot_as_float(polyglot_get_member(obj, "valueF"));   // 32 bit
  double d = polyglot_as_double(polyglot_get_member(obj, "valueD")); // 64 bit

  double sum = i + c + l + f + d; // 215
  if (b) {
    return (int)sum;
  } else {
    return 0;
  }
}
