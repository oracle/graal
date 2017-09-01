#include <truffle.h>

int main() {
  void *obj = truffle_import("foreign");

  int *i = (int *)truffle_read(obj, "valueI");       // 32 bit
  char *c = (char *)truffle_read(obj, "valueB");     // char = 8 bit in C ; byte = 8 bit in Java
  long *l = (long *)truffle_read(obj, "valueL");     // 64 bit
  float *f = (float *)truffle_read(obj, "valueF");   // 32 bit
  double *d = (double *)truffle_read(obj, "valueD"); // 64 bit

  double sum = 0;
  sum += truffle_read_idx_i(i, 0);
  sum += truffle_read_idx_l(l, 0);
  sum += truffle_read_idx_c(c, 0);
  sum += truffle_read_idx_f(f, 0);
  sum += truffle_read_idx_d(d, 0);

  sum += truffle_read_idx_i(i, 1);
  sum += truffle_read_idx_l(l, 1);
  sum += truffle_read_idx_c(c, 1);
  sum += truffle_read_idx_f(f, 1);
  sum += truffle_read_idx_d(d, 1);

  // 73
  return (int)sum;
}
