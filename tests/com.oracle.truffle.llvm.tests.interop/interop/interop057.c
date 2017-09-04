#include <stdint.h>
#include <truffle.h>

struct Foreign {
  uint32_t a;
  uint32_t b;
};

int main() {
  struct Foreign *foreign = truffle_import("foreign");
  
  if (foreign->a != 0) {
    return 100 + foreign->a;
  }
  
  if (foreign->b != 1) {
    return 200 + foreign->b;
  }
  
  foreign->a = 101;
  foreign->b = 102;
  
  return 0;
}