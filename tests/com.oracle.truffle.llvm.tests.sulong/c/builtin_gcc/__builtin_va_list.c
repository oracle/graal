#include <stdarg.h>

struct _point {
  long x;
  long y;
  long z;
};

int foo(int x, ...)
{
  __builtin_va_list argp;

  __builtin_va_start(argp, x);

  struct _point res1 = __builtin_va_arg(argp, struct _point);

  int result = res1.x + res1.y + res1.z;

  res1.x = 0;
  res1.y = 0;
  res1.z = 0;

  __builtin_va_end(argp);

  return result;
}

int main() {
  struct _point p = {19L, 13L, 9L};

  return foo(2, p) + foo(2, p);
}
