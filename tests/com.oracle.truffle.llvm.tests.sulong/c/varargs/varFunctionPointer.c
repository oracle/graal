#include <stdarg.h>
#include <stdio.h>

static int func(int val) {
  return val;
}

static int varfunc(int n, ...) {
  va_list ap;

  va_start(ap, n);

  typedef int (*func_t)(int);
  func_t fp = va_arg(ap, func_t);

  va_end(ap);

  return fp(0xdeadbeef);
}

int main(int argc, char **argv) {
  return varfunc(1, func) != 0xdeadbeef;
}
