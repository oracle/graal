#include <stdint.h>
#include <truffle.h>

int main() {
  uint32_t *foreign = truffle_import("foreign");
  
  if (foreign[0] != 0) {
    return 100 + foreign[0];
  }
  
  if (foreign[1] != 1) {
    return 200 + foreign[1];
  }
  
  foreign[0] = 101;
  foreign[1] = 102;
  
  return 0;
}