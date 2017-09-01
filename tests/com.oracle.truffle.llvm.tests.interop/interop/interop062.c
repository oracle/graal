#include <truffle.h>



int main() {
  void *p = truffle_import("object");
  
  void *p1 = truffle_handle_for_managed(p);
  void *p2 = truffle_handle_for_managed(p);

  if (p1 != p2) {
  	return 1;
  }
  return 0;
}