#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");

  double sum = 0;
  sum += truffle_invoke_i(obj, "addI", 1);   // 4
  sum += truffle_invoke_c(obj, "addB", 2);   // 3
  sum += truffle_invoke_l(obj, "addL", 3);   // 7
  sum += truffle_invoke_f(obj, "addF", 4.5); // 10
  sum += truffle_invoke_d(obj, "addD", 5.5); // 12
  return sum;                                // 36
}
