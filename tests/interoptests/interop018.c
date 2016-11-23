#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");
  return truffle_get_size(obj);
}
