#include <truffle.h>

typedef void *VALUE;

int main() {
  VALUE a = (VALUE) truffle_import("a"); 
  VALUE b = (VALUE) truffle_import("b"); 
  

  if (a != b) {
    return 1;
  } else {
    return 0;
  }
}