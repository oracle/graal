#include <truffle.h>



int main() {
  void *p = truffle_import("object");
  
  void *p1 = truffle_handle_for_managed(p);
  
  void *p2 = truffle_managed_from_handle(p1);
  void *p3 = truffle_managed_from_handle(p1);

  if (p2 != p3) {
  	return 1;
  }

  return 0;
}