#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");
  return (int)truffle_execute_d(obj, 40.5, 1.5);
}
