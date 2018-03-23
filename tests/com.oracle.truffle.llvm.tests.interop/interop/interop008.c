#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");
  char (*fn)(char, char) = (char (*)(char, char)) obj;
  return (int) fn(40, 2);
}
