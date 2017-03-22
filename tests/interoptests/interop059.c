#include <truffle.h>

typedef void *VALUE;

int main() {
  VALUE *foreign = truffle_import("foreign");
  
  if ((int) foreign[0] != 0) {
    return 100 + (int) foreign[0];
  }
  
  if ((int) foreign[1] != 1) { 
    return 200 + (int) foreign[1];
  }
  
  foreign[0] = 101;
  foreign[1] = 102; 
  
  
  return 0;
}