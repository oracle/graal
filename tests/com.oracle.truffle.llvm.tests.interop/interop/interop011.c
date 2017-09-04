#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");
  return (int)truffle_execute_f(obj, 40.5, 1.5);
}
