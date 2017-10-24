#include <stdlib.h>

int incr(int arg) { return arg + 1; }
int decr(int arg) { return arg - 1; }

int main() {
  int (**funcs)(int) = malloc(3 * sizeof(int (*)(int)));
  funcs[0] = &incr;
  funcs[1] = &incr;
  funcs[2] = &decr;
  int sum = 0;
  for (int i = 0; i < 3; i++) {
    sum += funcs[i](3);
  }
  free(funcs);
  return sum;
}
