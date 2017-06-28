#include <stdarg.h>
#include <stdio.h>

long double getResult(int num, ...) {
  va_list arguments;
  va_start(arguments, num);
  long double result = va_arg(arguments, long double)+va_arg(arguments, long double);
  va_end(arguments);
  return result;
}

int main() { return getResult(1, 3.0L, 5.0L); }
