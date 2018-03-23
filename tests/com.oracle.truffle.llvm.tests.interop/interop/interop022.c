#include <polyglot.h>

int main() {
  double *obj = (double *)polyglot_import("foreign");
  obj[0] = 30.0;
  obj[1] = 31.0;
  obj[2] = 32.0;
  obj[3] = 33.0;
  obj[4] = 34.0;
  return 0;
}
