#include <truffle.h>

typedef void *VALUE;

struct Foreign {
  VALUE a;
  VALUE b;
};

int main() {
  struct Foreign *foreign = truffle_import("foreign");
  
  if ((int) foreign->a != 0) {
    return 100 + (int) foreign->a;
  }
  
  if ((int) foreign->b != 1) {
    return 200 + (int) foreign->b;
  }
  
  foreign->a = 101;
  foreign->b = 102; 
  
  return 0;
}