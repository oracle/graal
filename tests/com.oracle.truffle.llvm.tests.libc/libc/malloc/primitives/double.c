#include <stdlib.h>

int main() {
  volatile double *d = malloc(sizeof(double));
  *d = 324234.123125;
  return (int)*d % 256;
}
