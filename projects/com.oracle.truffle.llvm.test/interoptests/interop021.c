#include <truffle.h>

int main() {
  double *obj = (int *)truffle_import("foreign");
  double sum = 0.0;
  sum += obj[0];
  sum += obj[1];
  sum += obj[2];
  sum += obj[3];
  sum += obj[4];
  return (int)sum;
}
