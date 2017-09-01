#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");
  return truffle_execute_i(obj, 40, 2);
}
