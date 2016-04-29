#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");
  return (int)truffle_execute_c(obj, 40, 2);
}
