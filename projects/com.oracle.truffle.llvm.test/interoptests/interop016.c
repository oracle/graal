#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");
  if (truffle_is_null(obj)) {
    return 42;
  } else {
    return 13;
  }
}
