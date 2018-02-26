#include <stdio.h>
#include <math.h>

double caller_f64(double (*)(double), double);

int main(void) {
  printf("%f\n", caller_f64(cos, 2.0));
  return 0;
}
