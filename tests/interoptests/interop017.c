#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");
  if (truffle_has_size(obj)) {
    return 42;
  } else {
    return 13;
  }
}
