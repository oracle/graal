#include <polyglot.h>

int main() {
  void *boxed_true = polyglot_import("boxed_true");
  void *boxed_false = polyglot_import("boxed_false");
  
  if (boxed_true) {
    // correct
  } else {
    return 1;
  }
  
  if (boxed_false) {
    return 2;
  } else {
    // correct
  }
  
  return 0;
}
