#include <truffle.h>

int main() {
  truffle_execute(truffle_import("foo"), truffle_read_n_bytes("foo\x00 bar\x80 bla", 10));
  return 36;
}
