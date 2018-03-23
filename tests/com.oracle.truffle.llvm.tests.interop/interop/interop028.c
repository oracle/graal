#include <polyglot.h>

int main() {
  void (*fn)(void *) = polyglot_import("foo");
  fn(polyglot_from_string_n("foo\x00 bar\x80 bla", 10, "ISO8859_1"));
  return 72;
}
