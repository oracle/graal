#include <stdio.h>
#include <complex.h>
#include <stdlib.h>

void testDouble() {
  volatile double complex z1 = 1.0 + 3.0 * I;
  volatile double complex z2 = 0.0 + 0.0 * I;
  volatile double complex conjugate1 = conj(z1);
  volatile double complex conjugate2 = conj(z2);
  if (creal(conjugate1) != 1.00 | cimag(conjugate1) != -3.0) {
    abort();
  }
  if (creal(conjugate2) != 0.00 | cimag(conjugate2) != 0.0) {
    abort();
  }
}

void testFloat() {
  volatile float complex z1 = 1.0 + 3.0 * I;
  volatile float complex z2 = 0.0 + 0.0 * I;
  volatile float complex conjugate1 = conj(z1);
  volatile float complex conjugate2 = conj(z2);
  if (crealf(conjugate1) != 1.00 | cimagf(conjugate1) != -3.0) {
    abort();
  }
  if (crealf(conjugate2) != 0.00 | cimagf(conjugate2) != 0.0) {
    abort();
  }
}

int main() {
  testDouble();
  // testFloat();
  return 0;
}
