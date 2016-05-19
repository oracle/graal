#include <math.h>
#include <stdlib.h>

volatile double zero = 0.0;
volatile double minusZero = -0.0;
volatile double negativeNumber = -3.0;
volatile double four = 4;

int main() {
  if (sqrt(zero) != 0.0) {
    abort();
  } else if (sqrt(minusZero) != -0) {
    abort();
  } else if (!isnan(sqrt(negativeNumber))) {
    abort();
  } else if (sqrt(four) != 2.0) {
    abort();
  } else if (sqrt(100000000000000.0) != 10000000.0) {
    abort();
  }
}
