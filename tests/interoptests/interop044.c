#include <truffle.h>

typedef void *VALUE;

int main() {
  VALUE a = (VALUE) truffle_import("a"); // An opaque TruffleObject
  VALUE b = (VALUE) truffle_import("b"); // A boxed primitive int, standing in for a tagged int as it would be in other VMs
  VALUE c = (VALUE) truffle_import("c"); // A boxed primitive double, standing in for a tagged double
  
  if (a == b) {
    return 1;
  }
  
  if (b == a) {
    return 2;
  }
  
  if (a == c) {
    return 3;
  }
  
  if (c == a) {
    return 4;
  }
  
  if (!(a != b)) {
    return 5;
  }
  
  if (!(b != a)) {
    return 6;
  }
  
  if (!(a != c)) {
    return 7;
  }
  
  if (!(c != a)) {
    return 8;
  }

  return 0;
}