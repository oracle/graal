#include <math.h>
#include <stdio.h>
#include <stdlib.h>

volatile double a = 0.5;

int main() {
  if (abs(cos(a) - 0.877583) >= 0.01) {
    abort();
  }
  if (abs(sin(a) - 0.479426) >= 0.01) {
    abort();
  }
}
