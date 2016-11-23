#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");
  return truffle_read_i(obj, "valueI");
}
