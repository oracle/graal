#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");

  truffle_write_b(obj, "valueBool", false);
  truffle_write_i(obj, "valueI", 2);   // 32 bit
  truffle_write_c(obj, "valueB", 3);   // char = 8 bit in C ; byte = 8 bit in Java
  truffle_write_l(obj, "valueL", 4);   // 64 bit
  truffle_write_f(obj, "valueF", 5.5); // 32 bit
  truffle_write_d(obj, "valueD", 6.5); // 64 bit
  return 0;
}
