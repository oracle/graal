#include <polyglot.h>

typedef void *VALUE;

int main() {
  VALUE a = (VALUE) polyglot_import("a"); 
  VALUE b = (VALUE) polyglot_import("b"); 
  

  if (a < b) {
    return 1;
  } else {
    return 0;
  }
}
