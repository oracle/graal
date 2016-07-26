#include <truffle.h>

int main() {
  truffle_execute(truffle_import("foo"), truffle_read_n_string("foo\x00 bar\x80 bla", 10));
  return 72;
}
