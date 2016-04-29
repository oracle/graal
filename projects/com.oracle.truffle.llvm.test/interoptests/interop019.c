#include <truffle.h>

int main() {
  int *obj = (int *)truffle_import("foreign");
  int sum = 0;
  sum += obj[0];
  sum += obj[1];
  sum += obj[2];
  sum += obj[3];
  sum += obj[4];
  return sum;
}
