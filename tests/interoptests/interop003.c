#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");

  bool b = truffle_read_b(obj, "valueBool");

  int i = truffle_read_i(obj, "valueI");    // 32 bit
  char c = truffle_read_c(obj, "valueB");   // char = 8 bit in C ; byte = 8 bit in Java
  long l = truffle_read_l(obj, "valueL");   // 64 bit
  float f = truffle_read_f(obj, "valueF");  // 32 bit
  double d = truffle_read_d(obj, "valueD"); // 64 bit

  double sum = i + c + l + f + d; // 215
  if (b) {
    return (int)sum;
  } else {
    return 0;
  }
}
