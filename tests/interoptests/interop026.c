#include <truffle.h>

int main() {
  truffle_execute(truffle_import("foo"), truffle_read_string("bar"));
  return 14;
}
