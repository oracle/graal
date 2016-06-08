#include <complex.h>

complex double conj(complex double z) {
  double a = creal(z);
  double b = cimag(z);
  return a + -b * I;
}

complex float conjf(complex float z) {
  float a = crealf(z);
  float b = cimagf(z);
  return a + -b * I;
}
